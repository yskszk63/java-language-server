'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as Path from "path";
import * as FS from "fs";
import { window, workspace, ExtensionContext, commands, tasks, Task, TaskExecution, ShellExecution, Uri, TaskDefinition, languages, IndentAction, Progress, ProgressLocation } from 'vscode';

import {LanguageClient, LanguageClientOptions, ServerOptions, NotificationType} from "vscode-languageclient";

/** Called when extension is activated */
export function activate(context: ExtensionContext) {
    console.log('Activating Java');

    let javaExecutablePath = findJavaExecutable('java');
    
    if (javaExecutablePath == null) {
        window.showErrorMessage("Couldn't locate java in $JAVA_HOME or $PATH");
        
        return;
    }
    // Options to control the language client
    let clientOptions: LanguageClientOptions = {
        // Register the server for java documents
        documentSelector: ['java'],
        synchronize: {
            // Synchronize the setting section 'java' to the server
            // NOTE: this currently doesn't do anything
            configurationSection: 'java',
            // Notify the server about file changes to 'javaconfig.json' files contain in the workspace
            fileEvents: [
                workspace.createFileSystemWatcher('**/javaconfig.json'),
                workspace.createFileSystemWatcher('**/pom.xml'),
                workspace.createFileSystemWatcher('**/WORKSPACE'),
                workspace.createFileSystemWatcher('**/BUILD'),
                workspace.createFileSystemWatcher('**/*.java')
            ]
        },
        outputChannelName: 'Java',
        revealOutputChannelOn: 4 // never
    }

    let fatJar = Path.resolve(context.extensionPath, "out", "fat-jar.jar");
    
    let args = [
        '-cp', fatJar, 
        '-Xverify:none', // helps VisualVM avoid 'error 62'
        '-Xdebug',
        // '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005',
        'org.javacs.Main'
    ];
    
    console.log(javaExecutablePath + ' ' + args.join(' '));
    
    // Start the child java process
    let serverOptions: ServerOptions = {
        command: javaExecutablePath,
        args: args,
        options: { cwd: context.extensionPath }
    }

    // Copied from typescript
    languages.setLanguageConfiguration('java', {
        indentationRules: {
            // ^(.*\*/)?\s*\}.*$
            decreaseIndentPattern: /^((?!.*?\/\*).*\*\/)?\s*[\}\]\)].*$/,
            // ^.*\{[^}"']*$
            increaseIndentPattern: /^((?!\/\/).)*(\{[^}"'`]*|\([^)"'`]*|\[[^\]"'`]*)$/,
            indentNextLinePattern: /^\s*(for|while|if|else)\b(?!.*[;{}]\s*(\/\/.*|\/[*].*[*]\/\s*)?$)/
        },
        onEnterRules: [
            {
                // e.g. /** | */
                beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
                afterText: /^\s*\*\/$/,
                action: { indentAction: IndentAction.IndentOutdent, appendText: ' * ' }
            }, {
                // e.g. /** ...|
                beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
                action: { indentAction: IndentAction.None, appendText: ' * ' }
            }, {
                // e.g.  * ...|
                beforeText: /^(\t|(\ \ ))*\ \*(\ ([^\*]|\*(?!\/))*)?$/,
                action: { indentAction: IndentAction.None, appendText: '* ' }
            }, {
                // e.g.  */|
                beforeText: /^(\t|(\ \ ))*\ \*\/\s*$/,
                action: { indentAction: IndentAction.None, removeText: 1 }
            },
            {
                // e.g.  *-----*/|
                beforeText: /^(\t|(\ \ ))*\ \*[^/]*\*\/\s*$/,
                action: { indentAction: IndentAction.None, removeText: 1 }
            }
        ]
    })

    // Create the language client and start the client.
    let client = new LanguageClient('java', 'Java Language Server', serverOptions, clientOptions);
    let disposable = client.start();

    // Push the disposable to the context's subscriptions so that the 
    // client can be deactivated on extension deactivation
    context.subscriptions.push(disposable);

    // Register test commands
	commands.registerCommand('java.command.test.run', runTest);

	// When the language client activates, register a progress-listener
	client.onReady().then(() => createProgressListeners(client));
}

function findJavaExecutable(binname: string) {
	binname = correctBinname(binname);

	// First search java.home setting
    let userJavaHome = workspace.getConfiguration('java').get('home') as string;

	if (userJavaHome != null) {
        console.log('Looking for java in settings java.home ' + userJavaHome + '...');

        let candidate = findJavaExecutableInJavaHome(userJavaHome, binname);

        if (candidate != null)
            return candidate;
	}

	// Then search each JAVA_HOME
    let envJavaHome = process.env['JAVA_HOME'];

	if (envJavaHome) {
        console.log('Looking for java in environment variable JAVA_HOME ' + envJavaHome + '...');

        let candidate = findJavaExecutableInJavaHome(envJavaHome, binname);

        if (candidate != null)
            return candidate;
	}

	// Then search PATH parts
	if (process.env['PATH']) {
        console.log('Looking for java in PATH');
        
		let pathparts = process.env['PATH'].split(Path.delimiter);
		for (let i = 0; i < pathparts.length; i++) {
			let binpath = Path.join(pathparts[i], binname);
			if (FS.existsSync(binpath)) {
				return binpath;
			}
		}
	}
    
	// Else return the binary name directly (this will likely always fail downstream) 
	return null;
}

function correctBinname(binname: string) {
	if (process.platform === 'win32')
		return binname + '.exe';
	else
		return binname;
}

function findJavaExecutableInJavaHome(javaHome: string, binname: string) {
    let workspaces = javaHome.split(Path.delimiter);

    for (let i = 0; i < workspaces.length; i++) {
        let binpath = Path.join(workspaces[i], 'bin', binname);

        if (FS.existsSync(binpath)) 
            return binpath;
    }

    return null;
}

// this method is called when your extension is deactivated
export function deactivate() {
}

interface JavaTestTask extends TaskDefinition {
    enclosingClass: string
    method: string
}

function runTest(sourceUri: string, enclosingClass: string, method: string): Thenable<TaskExecution> {
	let kind: JavaTestTask = {
		type: 'java.task.test',
        enclosingClass: enclosingClass,
        method: method,
    }
    var shell;
    let config = workspace.getConfiguration('java')
    // Run method or class
    if (method != null) {
        let command = config.get('testMethod') as string[]
        if (command.length == 0) {
            window.showErrorMessage('Set "java.testMethod" in .vscode/settings.json')
            shell = new ShellExecution('echo', ['Set "java.testMethod" in .vscode/settings.json, for example ["mvn", "test", "-Dtest=${class}#${method}"]'])
        } else {
            shell = templateCommand(command, enclosingClass, method)
        }
    } else {
        let command = config.get('testClass') as string[]
        if (command.length == 0) {
            window.showErrorMessage('Set "java.testClass" in .vscode/settings.json')
            shell = new ShellExecution('echo', ['Set "java.testClass" in .vscode/settings.json, for example ["mvn", "test", "-Dtest=${class}"]'])
        } else {
            shell = templateCommand(command, enclosingClass, method)
        }
    }
	let workspaceFolder = workspace.getWorkspaceFolder(Uri.parse(sourceUri))
	let task = new Task(kind, workspaceFolder, 'Java Test', 'Java Language Server', shell)
	return tasks.executeTask(task)
}

function templateCommand(command: string[], enclosingClass: string, method: string) {
    // Replace template parameters
    var replaced = []
    for (var i = 0; i < command.length; i++) {
        replaced[i] = command[i].replace('${class}', enclosingClass).replace('${method}', method)
    }
    // Populate env
    let env = {} as {[key: string]: string};
    let config = workspace.getConfiguration('java')
    if (config.has('home')) 
        env['JAVA_HOME'] = config.get('home')
    return new ShellExecution(replaced[0], replaced.slice(1), {env})
}

interface ProgressMessage {
    message: string 
    increment: number
}

function createProgressListeners(client: LanguageClient) {
	// Create a "checking files" progress indicator
	let progressListener = new class {
		progress: Progress<{message: string, increment?: number}>
		resolve: (nothing: {}) => void
		
		startProgress(message: string) {
            if (this.progress != null)
                this.endProgress();

            window.withProgress({title: message, location: ProgressLocation.Notification}, progress => new Promise((resolve, _reject) => {
                this.progress = progress;
                this.resolve = resolve;
            }));
		}
		
		reportProgress(message: string, increment: number) {
            if (increment == -1)
                this.progress.report({message});
            else 
                this.progress.report({message, increment})
		}

		endProgress() {
            if (this.progress != null) {
                this.resolve({});
                this.progress = null;
                this.resolve = null;
            }
		}
	}
	// Use custom notifications to drive progressListener
	client.onNotification(new NotificationType('java/startProgress'), (event: ProgressMessage) => {
		progressListener.startProgress(event.message);
	});
	client.onNotification(new NotificationType('java/reportProgress'), (event: ProgressMessage) => {
		progressListener.reportProgress(event.message, event.increment);
	});
	client.onNotification(new NotificationType('java/endProgress'), () => {
		progressListener.endProgress();
	});
}
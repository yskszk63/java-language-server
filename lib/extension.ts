'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as Path from "path";
import { window, workspace, ExtensionContext, commands, tasks, Task, TaskExecution, ShellExecution, Uri, TaskDefinition, languages, IndentAction, Progress, ProgressLocation } from 'vscode';

import {LanguageClient, LanguageClientOptions, ServerOptions, NotificationType} from "vscode-languageclient";

/** Called when extension is activated */
export function activate(context: ExtensionContext) {
    console.log('Activating Java');

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

    let launcherRelativePath = platformSpecificLauncher();
    let launcherPath = [context.extensionPath].concat(launcherRelativePath);
    let launcher = Path.resolve(...launcherPath);
    
    console.log(launcher);
    
    // Start the child java process
    let serverOptions: ServerOptions = {
        command: launcher,
        args: [],
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
    commands.registerCommand('java.command.findReferences', runFindReferences);

	// When the language client activates, register a progress-listener
	client.onReady().then(() => createProgressListeners(client));
}

// this method is called when your extension is deactivated
export function deactivate() {
}

function runFindReferences(uri: string, lineNumber: number, column: number) {
    // LSP is 0-based but VSCode is 1-based
    return commands.executeCommand('editor.action.findReferences', Uri.parse(uri), {lineNumber: lineNumber+1, column: column+1});
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

function platformSpecificLauncher(): string[] {
	switch (process.platform) {
		case 'win32':
            return ['dist', 'windows', 'bin', 'launcher'];

		case 'darwin':
            return ['dist', 'mac', 'bin', 'launcher'];
	}

	throw `unsupported platform: ${process.platform}`;
}
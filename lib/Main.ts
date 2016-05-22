'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as VSCode from 'vscode';
import * as Path from 'path';
import * as Finder from './Finder';
import {JavacServicesHolder} from './JavacServices';
import {JavaConfig} from './JavaConfig';
import {Autocomplete} from './Autocomplete';
import {Lint} from './Lint';
import {GotoDefinition} from './GotoDefinition';

const JAVA_MODE: VSCode.DocumentFilter = { language: 'java', scheme: 'file' };

/** Called when extension is activated */
export function activate(ctx: VSCode.ExtensionContext) {
    // Creates one javac for each javaconfig.json 
    let provideJavac = new JavacServicesHolder(VSCode.workspace.rootPath, ctx.extensionPath, onErrorWithoutRequestId);
    
    // Autocomplete feature
    let autocomplete = new Autocomplete(provideJavac);
    
    ctx.subscriptions.push(VSCode.languages.registerCompletionItemProvider(JAVA_MODE, autocomplete));
    
    // Go-to-symbol
    let goto = new GotoDefinition(provideJavac);
    
    VSCode.languages.registerDefinitionProvider('java', goto);
    
    /**
     * When a .java file is opened, ensure that compiler is started with appropriate config
     */
    function ensureJavac(document: VSCode.TextDocument) {
        if (document.languageId === 'java') {
            let config = Finder.findJavaConfig(VSCode.workspace.rootPath, document.fileName);
            
            provideJavac.getJavac(config.sourcePath, config.classPath, config.outputDirectory);
        }
    }
    
    // For each open document, ensure that a javac has been initialized with appropriate class and source paths
    VSCode.workspace.textDocuments.forEach(ensureJavac);
    
    // Every time a new document is open, ensure that a javac has been initialized
    ctx.subscriptions.push(VSCode.workspace.onDidOpenTextDocument(ensureJavac)); 
    
    // When a .java file is opened or save, compile it with javac and mark any errors
    let diagnosticCollection: VSCode.DiagnosticCollection = VSCode.languages.createDiagnosticCollection('java');
    let lint = new Lint(provideJavac, diagnosticCollection);
    
    // Lint the currently visible text editors
    VSCode.window.visibleTextEditors.forEach(editor => lint.doLint(editor.document))
    
    // Lint on save
    ctx.subscriptions.push(VSCode.workspace.onDidSaveTextDocument(document => lint.doLint(document)));
    
    // Lint on open
    ctx.subscriptions.push(VSCode.window.onDidChangeActiveTextEditor(editor => lint.doLint(editor.document)));
    
	ctx.subscriptions.push(diagnosticCollection);
    
    // When a javaconfig.json file is saved, invalidate cache
    ctx.subscriptions.push(VSCode.workspace.onDidSaveTextDocument(document => {
        if (Path.basename(document.fileName) == 'javaconfig.json')
            Finder.invalidateCaches();
    }));
}

function onErrorWithoutRequestId(message: string) {
    VSCode.window.showErrorMessage(message);
}

// this method is called when your extension is deactivated
export function deactivate() {
}
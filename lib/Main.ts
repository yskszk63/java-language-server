'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as V from 'vscode';
import * as P from 'path';
import * as F from './Finder';
import {JavacFactory} from './JavacServices';
import {JavaConfig} from './JavaConfig';
import {Autocomplete} from './Autocomplete';
import {Lint} from './Lint';
import {GotoDefinition} from './GotoDefinition';

const JAVA_MODE: V.DocumentFilter = { language: 'java', scheme: 'file' };


// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(ctx: V.ExtensionContext) {
    // Creates one javac for each javaconfig.json 
    let provideJavac = new JavacFactory(V.workspace.rootPath, ctx.extensionPath, onErrorWithoutRequestId);
    
    // Autocomplete
    let autocomplete = new Autocomplete(provideJavac);
    
    ctx.subscriptions.push(V.languages.registerCompletionItemProvider(JAVA_MODE, autocomplete));
    
    // Go-to-symbol
    let goto = new GotoDefinition(provideJavac);
    
    V.languages.registerDefinitionProvider('java', goto);
    
    // When a .java file is opened, ensure that compiler is started with appropriate config
    function ensureJavac(document: V.TextDocument) {
        if (document.languageId === 'java') {
            let config = F.findJavaConfig(V.workspace.rootPath, document.fileName);
            
            provideJavac.forConfig(config.sourcePath, config.classPath, config.outputDirectory);
        }
    }
    V.workspace.textDocuments.forEach(ensureJavac); // Currrently open documents
    ctx.subscriptions.push(V.workspace.onDidOpenTextDocument(ensureJavac)); // Documents opened in the future
    
    // When a .java file is opened or save, compile it with javac and mark any errors
    let diagnosticCollection: V.DiagnosticCollection = V.languages.createDiagnosticCollection('java');
    let lint = new Lint(provideJavac, diagnosticCollection);
    
    ctx.subscriptions.push(V.workspace.onDidOpenTextDocument(document => lint.onSaveOrOpen(document)));
    ctx.subscriptions.push(V.workspace.onDidSaveTextDocument(document => lint.onSaveOrOpen(document)));
	ctx.subscriptions.push(diagnosticCollection);
    
    // When a javaconfig.json file is saved, invalidate cache
    ctx.subscriptions.push(V.workspace.onDidSaveTextDocument(document => {
        if (P.basename(document.fileName) == 'javaconfig.json')
            F.invalidateCaches();
    }));
}

function onErrorWithoutRequestId(message: string) {
    V.window.showErrorMessage(message);
}

// this method is called when your extension is deactivated
export function deactivate() {
}
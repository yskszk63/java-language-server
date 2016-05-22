'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as VSCode from 'vscode';
import * as Path from 'path';
import * as Finder from './Finder';
import {JavacFactory} from './JavacServices';
import {JavaConfig} from './JavaConfig';
import {Autocomplete} from './Autocomplete';
import {Lint} from './Lint';
import {GotoDefinition} from './GotoDefinition';

const JAVA_MODE: VSCode.DocumentFilter = { language: 'java', scheme: 'file' };


// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(ctx: VSCode.ExtensionContext) {
    // Creates one javac for each javaconfig.json 
    let provideJavac = new JavacFactory(VSCode.workspace.rootPath, ctx.extensionPath, onErrorWithoutRequestId);
    
    // Autocomplete
    let autocomplete = new Autocomplete(provideJavac);
    
    ctx.subscriptions.push(VSCode.languages.registerCompletionItemProvider(JAVA_MODE, autocomplete));
    
    // Go-to-symbol
    let goto = new GotoDefinition(provideJavac);
    
    VSCode.languages.registerDefinitionProvider('java', goto);
    
    // When a .java file is opened, ensure that compiler is started with appropriate config
    function ensureJavac(document: VSCode.TextDocument) {
        if (document.languageId === 'java') {
            let config = Finder.findJavaConfig(VSCode.workspace.rootPath, document.fileName);
            
            provideJavac.forConfig(config.sourcePath, config.classPath, config.outputDirectory);
        }
    }
    VSCode.workspace.textDocuments.forEach(ensureJavac); // Currrently open documents
    ctx.subscriptions.push(VSCode.workspace.onDidOpenTextDocument(ensureJavac)); // Documents opened in the future
    
    // When a .java file is opened or save, compile it with javac and mark any errors
    let diagnosticCollection: VSCode.DiagnosticCollection = VSCode.languages.createDiagnosticCollection('java');
    let lint = new Lint(provideJavac, diagnosticCollection);
    
    // Lint the currently visible text editors
    VSCode.window.visibleTextEditors.forEach(editor => lint.onSaveOrOpen(editor.document))
    
    // Lint on save
    ctx.subscriptions.push(VSCode.workspace.onDidSaveTextDocument(document => lint.onSaveOrOpen(document)));
    
    // Lint on open
    ctx.subscriptions.push(VSCode.window.onDidChangeActiveTextEditor(editor => lint.onSaveOrOpen(editor.document)));
    
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
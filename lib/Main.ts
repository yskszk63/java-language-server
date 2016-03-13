'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as V from 'vscode';
import * as J from './JavacServices';

const JAVA_MODE: V.DocumentFilter = { language: 'java', scheme: 'file' };

let javac: J.JavacServices;
let diagnosticCollection: V.DiagnosticCollection;

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(ctx: V.ExtensionContext) {
	diagnosticCollection = V.languages.createDiagnosticCollection('java');
	ctx.subscriptions.push(diagnosticCollection);
    
	startBuildOnSaveWatcher(ctx.subscriptions);
}

function startBuildOnSaveWatcher(subscriptions: V.Disposable[]) {
    J.provideJavac([], []).then(newJavac => {
        javac = newJavac;
        
        javac.echo('javac server has started').then(response => {
            console.log(response);
        }).catch(err => console.error(err));
        
        V.workspace.onDidSaveTextDocument(document => {
            if (document.languageId !== 'java') 
                return;
                
            let javaConfig = V.workspace.getConfiguration('java');
            let textEditor = V.window.activeTextEditor;
            
            runBuilds(document, javaConfig);
        }, null, subscriptions);
    })
}

function runBuilds(document: V.TextDocument, 
                   javaConfig: V.WorkspaceConfiguration) {
    console.log('Check ' + document.fileName);
    
    javac.lint({
        path: document.fileName
    }).then(lint => {
        let uri = V.Uri.file(document.fileName);
        let diagnostics = lint.messages.map(asDiagnostic);
        
        diagnosticCollection.set(uri, diagnostics);
    }).catch(error => {
        console.error(error);
    });
}

function asDiagnostic(m: J.LintMessage): V.Diagnostic {
    let range = new V.Range(m.range.start.line, m.range.start.character, m.range.end.line, m.range.end.character);
    
    return new V.Diagnostic(range, m.message, m.severity);
}

// this method is called when your extension is deactivated
export function deactivate() {
}
'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';

const JAVA_MODE: vscode.DocumentFilter = { language: 'java', scheme: 'file' };

let diagnosticCollection: vscode.DiagnosticCollection;

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(ctx: vscode.ExtensionContext) {
	diagnosticCollection = vscode.languages.createDiagnosticCollection('java');
	ctx.subscriptions.push(diagnosticCollection);
    
	startBuildOnSaveWatcher(ctx.subscriptions);
}

function startBuildOnSaveWatcher(subscriptions: vscode.Disposable[]) {
	vscode.workspace.onDidSaveTextDocument(document => {
		if (document.languageId !== 'java') 
			return;
            
		let javaConfig = vscode.workspace.getConfiguration('java');
		let textEditor = vscode.window.activeTextEditor;
        
        runBuilds(document, javaConfig);
	}, null, subscriptions);
}

function runBuilds(document: vscode.TextDocument, javaConfig: vscode.WorkspaceConfiguration) {
    console.log('Check ' + document.fileName);
}

// this method is called when your extension is deactivated
export function deactivate() {
}
'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as V from 'vscode';
import * as J from './JavacServices';
import {JavaConfig} from './JavaConfig';
import {JavaCompletionProvider} from './JavaCompletionProvider';
import {JavaLint} from './JavaLint';

const JAVA_MODE: V.DocumentFilter = { language: 'java', scheme: 'file' };

let provideJavac: Promise<J.JavacServices> = J.provideJavac(V.workspace.rootPath, [], onErrorWithoutRequestId);
let diagnosticCollection: V.DiagnosticCollection = V.languages.createDiagnosticCollection('java');

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(ctx: V.ExtensionContext) {
	ctx.subscriptions.push(diagnosticCollection);
    ctx.subscriptions.push(V.languages.registerCompletionItemProvider(JAVA_MODE, new JavaCompletionProvider(provideJavac)))
    
	new JavaLint(provideJavac, ctx.subscriptions, diagnosticCollection);
}

function onErrorWithoutRequestId(message: string) {
    V.window.showErrorMessage(message);
}

// this method is called when your extension is deactivated
export function deactivate() {
}
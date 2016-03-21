
import * as V from 'vscode';
import * as F from './Finder';

import {JavacServices, LintMessage} from './JavacServices';
import {JavaConfig} from './JavaConfig';

export class JavaLint {
    
    constructor(private provideJavac: Promise<JavacServices>, 
                private subscriptions: V.Disposable[],
                private diagnosticCollection: V.DiagnosticCollection) {
        V.workspace.onDidSaveTextDocument(document => {
            if (document.languageId !== 'java') 
                return;
                
            let vsCodeJavaConfig = V.workspace.getConfiguration('java');
            let javaConfig = F.findJavaConfig(V.workspace.rootPath, document.fileName);
            let textEditor = V.window.activeTextEditor;
            
            this.runBuilds(document, vsCodeJavaConfig, javaConfig);
        }, null, subscriptions);
        
    }
    
    private startBuildOnSaveWatcher() {
        V.workspace.onDidSaveTextDocument(document => {
            if (document.languageId !== 'java') 
                return;
                
            let vsCodeJavaConfig = V.workspace.getConfiguration('java');
            let javaConfig = F.findJavaConfig(V.workspace.rootPath, document.fileName);
            let textEditor = V.window.activeTextEditor;
            
            this.runBuilds(document, vsCodeJavaConfig, javaConfig);
        }, null, this.subscriptions);
    }
    
    private runBuilds(document: V.TextDocument, 
                   vsCodeJavaConfig: V.WorkspaceConfiguration,
                   javaConfig: JavaConfig) {
        this.provideJavac.then(javac => {
            javac.lint({
                path: document.fileName,
                config: javaConfig
            }).then(lint => {
                this.diagnosticCollection.clear();
                
                for (let uri of Object.keys(lint.messages)) {
                    let file = V.Uri.file(uri);
                    let diagnostics = lint.messages[uri].map(asDiagnostic);
                    
                    this.diagnosticCollection.set(file, diagnostics);
                }
            }).catch(error => {
                V.window.showErrorMessage(error);
            });
        });                       
    }
}

function asDiagnostic(m: LintMessage): V.Diagnostic {
    let range = new V.Range(m.range.start.line, m.range.start.character, m.range.end.line, m.range.end.character);
    
    return new V.Diagnostic(range, m.message, m.severity);
}
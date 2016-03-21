
import * as V from 'vscode';

import {findJavaConfig} from './Finder';
import {JavacServices, LintMessage} from './JavacServices';
import {JavaConfig} from './JavaConfig';

export class Lint {
    
    constructor(private provideJavac: Promise<JavacServices>, 
                private diagnosticCollection: V.DiagnosticCollection) {
    }
    
    public onSave(document: V.TextDocument) {
        if (document.languageId !== 'java') 
            return;
            
        let vsCodeJavaConfig = V.workspace.getConfiguration('java');
        let textEditor = V.window.activeTextEditor;
        
        this.runBuilds(document, vsCodeJavaConfig);
    }
    
    private runBuilds(document: V.TextDocument, 
                      vsCodeJavaConfig: V.WorkspaceConfiguration) {
        this.provideJavac.then(javac => {
            javac.lint({
                path: document.fileName,
                config: findJavaConfig(V.workspace.rootPath, document.fileName)
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
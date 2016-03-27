
import * as V from 'vscode';

import {findJavaConfig} from './Finder';
import {JavacFactory, JavacServices, LintMessage} from './JavacServices';
import {JavaConfig} from './JavaConfig';

export class Lint {
    
    constructor(private javac: JavacFactory, 
                private diagnosticCollection: V.DiagnosticCollection) {
    }
    
    public onSaveOrOpen(document: V.TextDocument) {
        if (document.languageId !== 'java') 
            return;
            
        let vsCodeJavaConfig = V.workspace.getConfiguration('java');
        let textEditor = V.window.activeTextEditor;
        
        this.runBuilds(document, vsCodeJavaConfig);
    }
    
    private runBuilds(document: V.TextDocument, 
                      vsCodeJavaConfig: V.WorkspaceConfiguration) {
        let config = findJavaConfig(V.workspace.rootPath, document.fileName);
        let javac = this.javac.forConfig(config.sourcePath, config.classPath, config.outputDirectory);
        
        javac.then(javac => {
            javac.lint({
                path: document.fileName
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
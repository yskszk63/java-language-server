
import * as VSCode from 'vscode';

import {findJavaConfig} from './Finder';
import {JavacFactory, JavacServices, LintMessage} from './JavacServices';
import {JavaConfig} from './JavaConfig';

export class Lint {
    
    constructor(private javac: JavacFactory, 
                private diagnosticCollection: VSCode.DiagnosticCollection) {
    }
    
    public onSaveOrOpen(document: VSCode.TextDocument) {
        if (document.languageId !== 'java') 
            return;
            
        let vsCodeJavaConfig = VSCode.workspace.getConfiguration('java');
        let textEditor = VSCode.window.activeTextEditor;
        
        this.runBuilds(document, vsCodeJavaConfig);
    }
    
    private runBuilds(document: VSCode.TextDocument, 
                      vsCodeJavaConfig: VSCode.WorkspaceConfiguration) {
        let config = findJavaConfig(VSCode.workspace.rootPath, document.fileName);
        let javac = this.javac.forConfig(config.sourcePath, config.classPath, config.outputDirectory);
        
        javac.then(javac => {
            javac.lint({
                path: document.fileName
            }).then(lint => {
                this.diagnosticCollection.clear();
                
                for (let uri of Object.keys(lint.messages)) {
                    let file = VSCode.Uri.file(uri);
                    let diagnostics = lint.messages[uri].map(asDiagnostic);
                    
                    this.diagnosticCollection.set(file, diagnostics);
                }
            }).catch(error => {
                VSCode.window.showErrorMessage(error);
            });
        });                       
    }
}

function asDiagnostic(m: LintMessage): VSCode.Diagnostic {
    let range = new VSCode.Range(m.range.start.line, m.range.start.character, m.range.end.line, m.range.end.character);
    
    return new VSCode.Diagnostic(range, m.message, m.severity);
}
import * as VSCode from 'vscode';
import {JavacServicesHolder, JavacServices, ResponseAutocomplete, AutocompleteSuggestion} from './JavacServices';
import {findJavaConfig} from './Finder';

/**
 * Provides autocomplete feature by calling javac service
 */
export class Autocomplete implements VSCode.CompletionItemProvider {
    constructor(private javac: JavacServicesHolder) { }
    
    provideCompletionItems(document: VSCode.TextDocument, 
                           position: VSCode.Position,
                           token: VSCode.CancellationToken): Promise<VSCode.CompletionItem[]> {
        let text = document.getText();
        let path = document.uri.fsPath;
        let config = findJavaConfig(VSCode.workspace.rootPath, document.fileName);
        let javac = this.javac.getJavac(config.sourcePath, config.classPath, config.outputDirectory);
        let response = javac.then(javac => javac.autocomplete({path, text, position}));
        
        return response.then(asCompletionItems);
    }
}

/**
 * Convert JSON (returned by javac service process) to CompletionItem
 */
function asCompletionItems(response: ResponseAutocomplete): VSCode.CompletionItem[] {
    return response.suggestions.map(asCompletionItem);
}

function asCompletionItem(s: AutocompleteSuggestion): VSCode.CompletionItem {
    let item = new VSCode.CompletionItem(s.label);
    
    item.detail = s.detail;
    item.documentation = s.documentation;
    item.filterText = s.filterText;
    item.insertText = s.insertText;
    item.kind = s.kind;
    item.label = s.label;
    item.sortText = s.sortText;
    
    return item;
}
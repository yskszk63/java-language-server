import * as V from 'vscode';
import * as J from './JavacServices';
import * as F from './Finder';

export class GotoDefinition implements V.DefinitionProvider {
    constructor (private javac: J.JavacFactory) { }
    
    provideDefinition(document: V.TextDocument, position: V.Position, token: V.CancellationToken): Promise<V.Location[]> {
        let text = document.getText();
        let path = document.uri.fsPath;
        let config = F.findJavaConfig(V.workspace.rootPath, document.fileName)
        let javac = this.javac.forConfig(config.sourcePath, config.classPath, config.outputDirectory);
        let response = javac.then(javac => javac.goto({path, text, position}));
        
        return response.then(asDefinition);
    } 
}

function asDefinition(response: J.ResponseGoto): V.Location[] {
    return response.definitions.map(asLocation);
}

function asLocation(d: J.GotoLocation): V.Location {
    let start = asPosition(d.range.start);
    let end = asPosition(d.range.end);
    let range = new V.Range(start, end);
    
    return new V.Location(V.Uri.parse(d.uri), range);
}

function asPosition(r: J.Position): V.Position {
    return new V.Position(r.line, r.character);
}
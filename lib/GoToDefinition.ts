import * as V from 'vscode';
import * as J from './JavacServices';
import * as F from './Finder';

export class GoToDefinition implements V.DefinitionProvider {
    constructor (private provideJavac: Promise<J.JavacServices>) { }
    
    provideDefinition(document: V.TextDocument, position: V.Position, token: V.CancellationToken): Promise<V.Location[]> {
        let text = document.getText();
        let path = document.uri.fsPath;
        let config = F.findJavaConfig(V.workspace.rootPath, document.fileName)
        let response = this.provideJavac.then(javac => javac.goto({path, text, position, config}));
        
        return response.then(asDefinition);
    } 
}

function asDefinition(response: J.ResponseGoTo): V.Location[] {
    return response.definitions.map(asLocation);
}

function asLocation(d: J.GoToLocation): V.Location {
    let start = asPosition(d.range.start);
    let end = asPosition(d.range.end);
    let range = new V.Range(start, end);
    
    return new V.Location(V.Uri.parse(d.uri), range);
}

function asPosition(r: J.Position): V.Position {
    return new V.Position(r.line, r.character);
}
import * as VSCode from 'vscode';
import * as Finder from './Finder';
import {JavacServicesHolder, GotoLocation, Position, ResponseGoto} from './JavacServices';

/**
 * Provides go-to-definition by calling javac service
 */
export class GotoDefinition implements VSCode.DefinitionProvider {
    constructor (private javac: JavacServicesHolder) { }
    
    provideDefinition(document: VSCode.TextDocument, position: VSCode.Position, token: VSCode.CancellationToken): Promise<VSCode.Location[]> {
        let text = document.getText();
        let path = document.uri.fsPath;
        let config = Finder.findJavaConfig(VSCode.workspace.rootPath, document.fileName)
        let javac = this.javac.getJavac(config.sourcePath, config.classPath, config.outputDirectory);
        let response = javac.then(javac => javac.goto({path, text, position}));
        
        return response.then(asDefinition);
    } 
}

function asDefinition(response: ResponseGoto): VSCode.Location[] {
    return response.definitions.map(asLocation);
}

function asLocation(d: GotoLocation): VSCode.Location {
    let start = asPosition(d.range.start);
    let end = asPosition(d.range.end);
    let range = new VSCode.Range(start, end);
    
    return new VSCode.Location(VSCode.Uri.parse(d.uri), range);
}

function asPosition(r: Position): VSCode.Position {
    return new VSCode.Position(r.line, r.character);
}
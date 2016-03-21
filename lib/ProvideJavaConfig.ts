import * as F from './Finder';
import * as V from 'vscode';
import * as P from 'path';

import {JavaConfig} from './JavaConfig';

let cached: {[path: string]: JavaConfig} = {};

/**
 * Provide the latest saved version of javaconfig.json 
 * in the nearest parent directory of [fileName]
 */
export function getConfig(fileName: string) {
    if (cached[fileName] == null)
        return refreshConfig(fileName);
    else
        return cached[fileName];
}

function refreshConfig(fileName: string) {
    cached[fileName] = F.findJavaConfig(V.workspace.rootPath, fileName);
    
    return cached[fileName];
}

/**
 * Invalidate cached javaconfig.json files when they are saved
 */
export function onSaveConfig(document: V.TextDocument) {
    if (P.basename(document.fileName) === 'javaconfig.json') {
        cached[document.fileName] = null;
    }
}
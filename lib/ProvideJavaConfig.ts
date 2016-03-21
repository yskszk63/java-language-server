import * as F from './Finder';
import * as V from 'vscode';
import * as P from 'path';

import {JavaConfig} from './JavaConfig';

var cached: JavaConfig;

export function getConfig(fileName: string) {
    if (cached == null)
        return refreshConfig(fileName);
    else
        return cached;
}

function refreshConfig(fileName: string) {
    cached = F.findJavaConfig(V.workspace.rootPath, fileName);
    
    return cached;
}

export function onSaveConfig(document: V.TextDocument) {
    if (P.basename(document.fileName) === 'javaconfig.json') {
        refreshConfig(document.fileName);
    }
}
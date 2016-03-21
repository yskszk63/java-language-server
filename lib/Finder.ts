
'use strict';

import FS = require('fs');
import Path = require('path');
import OS = require('os');

import {JavaConfig} from './JavaConfig';

let binPathCache: { [bin: string]: string; } = {};
let runtimePathCache: string = null;

export function findJavaExecutable(binname: string) {
	binname = correctBinname(binname);
	if (binPathCache[binname]) return binPathCache[binname];

	// First search each JAVA_HOME bin folder
	if (process.env['JAVA_HOME']) {
		let workspaces = process.env['JAVA_HOME'].split(Path.delimiter);
		for (let i = 0; i < workspaces.length; i++) {
			let binpath = Path.join(workspaces[i], 'bin', binname);
			if (FS.existsSync(binpath)) {
				binPathCache[binname] = binpath;
				return binpath;
			}
		}
	}

	// Then search PATH parts
	if (process.env['PATH']) {
		let pathparts = process.env['PATH'].split(Path.delimiter);
		for (let i = 0; i < pathparts.length; i++) {
			let binpath = Path.join(pathparts[i], binname);
			if (FS.existsSync(binpath)) {
				binPathCache[binname] = binpath;
				return binpath;
			}
		}
	}
    
	// Else return the binary name directly (this will likely always fail downstream) 
	binPathCache[binname] = binname;
	return binname;
}

function correctBinname(binname: string) {
	if (process.platform === 'win32')
		return binname + '.exe';
	else
		return binname;
}

let javaConfigCache: {[file: string]: JavaConfig} = {};
let locationCache: {[file: string]: string} = {};

const DEFAULT_JAVA_CONFIG: JavaConfig = {
    sourcePath: ["src"],
    outputDirectory: "target",
    classPath: [] 
}

/**
 * Get the latest saved version of javaconfig.json 
 * in the nearest parent directory of [fileName]
 */
export function findJavaConfig(workspaceRoot: string, javaSource: string): JavaConfig {
    workspaceRoot = Path.normalize(workspaceRoot);
    javaSource = Path.resolve(workspaceRoot, javaSource);
    
    let location = findLocation(workspaceRoot, javaSource);
    
    return loadConfig(location);
}

/**
 * Forget all the cached javaconfig.json locations and contents
 */
export function invalidateCaches() {
    javaConfigCache = {};
    locationCache = {};
}

function loadConfig(javaConfig: string) {
    if (javaConfig == null)
        return DEFAULT_JAVA_CONFIG;
        
    if (!javaConfigCache.hasOwnProperty(javaConfig)) {
        let text = FS.readFileSync(javaConfig, 'utf8');
        let json = JSON.parse(text);
        
        javaConfigCache[javaConfig] = json;
    }
    
    return javaConfigCache[javaConfig];
}

function findLocation(workspaceRoot: string, javaSource: string) {
    if (!locationCache.hasOwnProperty(javaSource))
        locationCache[javaSource] = doFindLocation(workspaceRoot, javaSource);
        
    return locationCache[javaSource];
}

function doFindLocation(workspaceRoot: string, javaSource: string): string {
    var pointer = Path.dirname(javaSource);
    
    while (true) {
        let candidate = Path.resolve(pointer, 'javaconfig.json');
        
        if (FS.existsSync(candidate))
            return candidate;
            
        else if (pointer === workspaceRoot || pointer === Path.dirname(pointer))
            return null;
        else 
            pointer = Path.dirname(pointer);
    }
}

'use strict';

import fs = require('fs');
import path = require('path');
import os = require('os');

import {JavaConfig} from './JavaConfig';

let binPathCache: { [bin: string]: string; } = {};
let runtimePathCache: string = null;

export function findJavaExecutable(binname: string) {
	binname = correctBinname(binname);
	if (binPathCache[binname]) return binPathCache[binname];

	// First search each JAVA_HOME bin folder
	if (process.env['JAVA_HOME']) {
		let workspaces = process.env['JAVA_HOME'].split(path.delimiter);
		for (let i = 0; i < workspaces.length; i++) {
			let binpath = path.join(workspaces[i], 'bin', binname);
			if (fs.existsSync(binpath)) {
				binPathCache[binname] = binpath;
				return binpath;
			}
		}
	}

	// Then search PATH parts
	if (process.env['PATH']) {
		let pathparts = process.env['PATH'].split(path.delimiter);
		for (let i = 0; i < pathparts.length; i++) {
			let binpath = path.join(pathparts[i], binname);
			if (fs.existsSync(binpath)) {
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
    workspaceRoot = path.normalize(workspaceRoot);
    javaSource = path.resolve(workspaceRoot, javaSource);
    
    let location = findLocation(workspaceRoot, javaSource);
    
    return loadConfig(location);
}

export function invalidateCaches() {
    javaConfigCache = {};
    locationCache = {};
}

function loadConfig(javaConfig: string) {
    if (javaConfig == null)
        return DEFAULT_JAVA_CONFIG;
        
    if (!javaConfigCache.hasOwnProperty(javaConfig)) {
        let text = fs.readFileSync(javaConfig, 'utf8');
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
    var pointer = path.dirname(javaSource);
    
    while (true) {
        let candidate = path.resolve(pointer, 'javaconfig.json');
        
        if (fs.existsSync(candidate))
            return candidate;
            
        else if (pointer === workspaceRoot || pointer === path.dirname(pointer))
            return null;
        else 
            pointer = path.dirname(pointer);
    }
}
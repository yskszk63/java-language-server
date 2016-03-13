
'use strict';

import fs = require('fs');
import path = require('path');
import os = require('os');

let binPathCache: { [bin: string]: string; } = {};
let runtimePathCache: string = null;

export function getJavaBinPath(binname: string) {
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
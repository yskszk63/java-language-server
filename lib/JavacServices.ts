import * as ChildProcess from 'child_process';
import * as Path from 'path';
import * as PortFinder from 'portfinder';
import * as Net from 'net';
// Don't import members, just types, otherwise the tests will break
import {DiagnosticSeverity,CompletionItemKind} from 'vscode';
import {findJavaExecutable, findJavaConfig} from './Finder';
import split = require('split');

PortFinder.basePort = 55220;

/**
 * Holds a single instance of JavacServices with a particular source path, class path, and output directory.
 * If classpath, source path, or output directory change, starts a new JavacServices.
 */
export class JavacServicesHolder {
    constructor(private projectDirectoryPath: string, 
                private extensionDirectoryPath: string, 
                private onError: (message: string) => void) {
        
    }
    
    private cachedSourcePath: string[];
    private cachedClassPath: string[];
    private cachedOutputDirectory: string;
    private cachedCompiler: Promise<JavacServices>;
    
    /**
     * Get an instance of JavacServices with given source path, class path, output directory.
     * If these arguments are the same as the last time this function was called,
     * returns the same, cached JavacServices.
     */
    getJavac(sourcePath: string[], classPath: string[], outputDirectory: string): Promise<JavacServices> {
        sourcePath = sourcePath.sort();
        classPath = classPath.sort();
        
        if (!sortedArrayEquals(sourcePath, this.cachedSourcePath) || 
            !sortedArrayEquals(classPath, this.cachedClassPath) || 
            outputDirectory != this.cachedOutputDirectory) {
            // TODO kill old compiler
            
            this.cachedSourcePath = sourcePath;
            this.cachedClassPath = classPath;
            this.cachedOutputDirectory = outputDirectory;
            this.cachedCompiler = this.newJavac(sourcePath, classPath, outputDirectory);
        }
        
        return this.cachedCompiler;
    }
    
    private newJavac(sourcePath: string[], 
                     classPath: string[], 
                     outputDirectory: string): Promise<JavacServices> {
        return new Promise((resolve, reject) => {
            let javaPath = findJavaExecutable('java');

            PortFinder.getPort((err, port) => {
                let javacServicesClassPath = [Path.resolve(this.extensionDirectoryPath, "out", "fat-jar.jar")];           
                let javac = new JavacServices(javaPath, 
                                            javacServicesClassPath, 
                                            port, 
                                            this.projectDirectoryPath, 
                                            this.extensionDirectoryPath,
                                            sourcePath, 
                                            classPath, 
                                            outputDirectory, 
                                            this.onError);
                resolve(javac);
            });
        });
    }
}

function sortedArrayEquals(xs: string[], ys: string[]) {
    if (xs == null && ys == null)
        return true;
    else if (xs == null || ys == null)
        return false;
    else if (xs.length != ys.length)
        return false;
    else {
        for (var i = 0; i < xs.length; i++) {
            if (xs[i] != ys[i])
                return false;
        }
        
        return true;
    }
}

interface JavacOptions {
    /**
     * Path to java file
     */
    path: string;
    
    /**
     * Java source.
     * If not specified, file at [path] will be read.
     */
    text?: string;
}

export interface RequestLint extends JavacOptions {
}

export interface RequestAutocomplete extends JavacOptions {
    position: Position;
}

export interface ResponseLint {
    messages: {
        [uri: string]: LintMessage[];
    }
}

export interface Range {
    /**
     * The start position. It is before or equal to [end](#Range.end).
     * @readonly
     */
    start: Position;

    /**
     * The end position. It is after or equal to [start](#Range.start).
     * @readonly
     */
    end: Position;
}

export interface Position {
    /**
     * The zero-based line value.
     * @readonly
     */
    line: number;

    /**
     * The zero-based character value.
     * @readonly
     */
    character: number;
}

export interface LintMessage {
    uri: string;
    
    /**
     * The range to which this diagnostic applies.
     */
    range: Range;

    /**
     * The human-readable message.
     */
    message: string;

    /**
     * A human-readable string describing the source of this
     * diagnostic, e.g. 'typescript' or 'super lint'.
     */
    source: string;

    /**
     * The severity, default is [error](#DiagnosticSeverity.Error).
     */
    severity: DiagnosticSeverity;
}

/** The suggestion */
export interface AutocompleteSuggestion {
    /**
     * The label of this completion item. By default
     * this is also the text that is inserted when selecting
     * this completion.
     */
    label: string;

    /**
     * The kind of this completion item. Based on the kind
     * an icon is chosen by the editor.
     */
    kind: CompletionItemKind;

    /**
     * A human-readable string with additional information
     * about this item, like type or symbol information.
     */
    detail: string;

    /**
     * A human-readable string that represents a doc-comment.
     */
    documentation: string;

    /**
     * A string that should be used when comparing this item
     * with other items. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    sortText: string;

    /**
     * A string that should be used when filtering a set of
     * completion items. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    filterText: string;

    /**
     * A string that should be inserted in a document when selecting
     * this completion. When `falsy` the [label](#CompletionItem.label)
     * is used.
     */
    insertText: string;
}

export interface ResponseAutocomplete {
    suggestions: AutocompleteSuggestion[];
}

export interface RequestGoto extends JavacOptions {
    position: Position;
}

export interface ResponseGoto {
    definitions: GotoLocation[];
}

export interface GotoLocation {
    uri: string;
    range: Range;
}

/**
 * Starts an external java process running org.javacs.Main
 * Invokes functions on this process using a local network socket.
 */
export class JavacServices {
    /** Socket we use to communicate with external java process */
    private socket: Promise<Net.Socket>;

    /** # requests we've made so far, used to generate unique request ids */
    private requestCounter = 0;

    /** What to do after each response comes back */
    private requestCallbacks: { [requestId: number]: (response: Response) => void } = {};

    constructor(javaExecutablePath: string,
                javacServicesClassPath: string[],
                port: number,
                projectDirectoryPath: string, 
                extensionDirectoryPath: string,
                sourcePath: string[],
                classPath: string[],
                outputDirectory: string,
                private onError: (message: string) => void) {
        var args = ['-cp', javacServicesClassPath.join(':')];

        //args.push('-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005');
        args.push('-Djavacs.port=' + port);
        args.push('-Djavacs.sourcePath=' + sourcePath.join(':'));
        args.push('-Djavacs.classPath=' + classPath.join(':'));
        args.push('-Djavacs.outputDirectory=' + outputDirectory);
        args.push('org.javacs.Main');

        console.log(javaExecutablePath + ' ' + args.join(' '));

        // Connect to socket that will be used for communication
        this.socket = new Promise((resolve, reject) => {
            Net.createServer(socket => {
                console.log('Child process connected on port ' + port);

                // Handle responses from the child java process
                socket
                    .pipe(split())
                    .on('data', response => {
                        if (response.length > 0)
                            this.handleResponse(response);
                    });

                resolve(socket);
            }).listen(port, () => {
                var options = { stdio: 'inherit', cwd: projectDirectoryPath };
                
                // Start the child java process
                ChildProcess.spawn(javaExecutablePath, args, options);
            });
        });
    }

    echo(message: string): Promise<string> {
        return this.doRequest('echo', message);
    }

    lint(request: RequestLint): Promise<ResponseLint> {
        return this.doRequest('lint', request);
    }

    autocomplete(request: RequestAutocomplete): Promise<ResponseAutocomplete> {
        return this.doRequest('autocomplete', request);
    }
    
    goto(request: RequestGoto): Promise<ResponseGoto> {
        return this.doRequest('goto', request);
    }

    private doRequest(type: string, payload: any): Promise<any> {
        var requestId = this.requestCounter++;

        return new Promise((resolve, reject) => {
            let request: Request = { requestId: requestId };

            // Set payload, using request type as key for easy deserialization
            request[type] = payload;
            
            // Send request to child process
            this.socket.then(socket => socket.write(JSON.stringify(request)));

            // Set callback handler
            this.requestCallbacks[requestId] = response => {
                if (response.error != null)
                    reject(response.error.message);
                else
                    resolve(response[type]);
            }
        });
    }

    private handleResponse(message: string) {
        var response: Response = JSON.parse(message);
        
        if (response.error)
            this.onError(response.error.message);
        
        if (response.requestId != null) {
            var todo = this.requestCallbacks[response.requestId];

            this.requestCallbacks[response.requestId] = null;

            if (!todo)
                console.error('No callback registered for request id ' + response.requestId);
            else 
                todo(response);
        }
    }
}

/**
 * Common format of all requests
 */
interface Request {
    /**
     * Sequential ID of this request
     */
    requestId: number;
    
    /**
     * Arguments specific to the request type (line, autocomplete, etc)
     */
    [requestType: string]: any;
}

interface Response {
    /**
     * Matches Request#requestId
     */
    requestId: number;
    
    /**
     * Response data specific to the request type (line, autocomplete, etc)
     */
    [requestType: string]: any;
    
    /**
     * Error message, if there is a general error
     */
    error?: {
        message: string;
    }
}

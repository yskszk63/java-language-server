import child_process = require('child_process');
import split = require('split');
import path = require('path');
import PortFinder = require('portfinder');
import Net = require('net');
import * as Maven from './Maven';
import {MavenDependency} from './JavaConfig';
// Don't import members, just types, otherwise the tests will break
import {DiagnosticSeverity,CompletionItemKind} from 'vscode';
import {findJavaExecutable} from './Finder';
import {JavaConfig} from './JavaConfig';

export function provideJavac(projectDirectoryPath: string, 
                             mavenDependencies: MavenDependency[] = [],
                             onErrorWithoutRequestId: (message: string) => void): Promise<JavacServices> {
    return new Promise((resolve, reject) => {
        PortFinder.basePort = 55220;
        
        var javaPath = findJavaExecutable('java');
        var dependencies = mavenDependencies.concat([
            {
                "groupId": "com.fivetran",
                "artifactId": "javac-services",
                "version": "0.1-SNAPSHOT"
            }
        ]);

        PortFinder.getPort((err, port) => {
            Maven.dependencies({dependencies}, (err, mvnResults) => {
                if (err)
                    reject(err);
                else {
                    var mvnClasspath = mvnResults.classpath;

                    resolve(new JavacServices(projectDirectoryPath, javaPath, mvnClasspath, port, onErrorWithoutRequestId));
                }
            });
        });
    });
}

interface JavacOptions {
    path: string;
    text?: string;
    config?: JavaConfig;
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

export class JavacServices {
    private socket: Promise<Net.Socket>;

    /** # requests we've made so far, used to generate unique request ids */
    private requestCounter = 0;

    /** What to do after each response comes back */
    private requestCallbacks: { [requestId: number]: (response: Response) => void } = {};

    constructor(projectDirectoryPath: string,
                javaExecutablePath: string,
                classPath: string[],
                port: number,
                private onErrorWithoutRequestId: (message: string) => void) {

        var args = ['-cp', classPath.join(':')];

        //args.push('-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005');
        args.push('-DservicePort=' + port);
        args.push('-DprojectDirectory=' + projectDirectoryPath);
        args.push('com.fivetran.javac.Main');

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
                child_process.spawn(javaExecutablePath, args, options);
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
        
        if (response.requestId == null) {
            if (response.error)
                this.onErrorWithoutRequestId(response.error.message);
        }
        else {
            var todo = this.requestCallbacks[response.requestId];

            this.requestCallbacks[response.requestId] = null;

            if (!todo)
                console.error('No callback registered for request id ' + response.requestId);
            else 
                todo(response);
        }
    }
}

interface Request {
    requestId: number;
    [requestType: string]: any;
}

interface Response {
    requestId: number;
    [requestType: string]: any;
    error?: {
        message: string;
    }
}

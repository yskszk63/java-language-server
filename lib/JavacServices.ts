import child_process = require('child_process');
import split = require('split');
import path = require('path');
import PortFinder = require('portfinder');
import Net = require('net');
import * as Maven from './Maven';
import {MavenDependency} from './JavaConfig';

export function provideJavac(classpath: string[], mavenDependencies: MavenDependency[] = []): Promise<JavacServices> {
    return new Promise((resolve, reject) => {
        PortFinder.basePort = 55220;

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
                    var combinedClasspath = mvnClasspath.concat(classpath);

                    resolve(new JavacServices('.', 'java', combinedClasspath, port, 'inherit'));
                }
            });
        });
    });
}

interface JavacOptions {
    path: string;
    text?: string;
    classPath?: string[];
    sourcePath?: string[];
    outputDirectory?: string;
}

export interface RequestLint extends JavacOptions {
}

export interface RequestAutocomplete extends JavacOptions {
    row: number;
    column: number;
}

export interface ResponseLint {
    messages: LintMessage[];
}

export interface Position {
    row: number;
    column: number;
}

export interface LintMessage {
    type: string;
    message: string;
    range: {
        start: Position;
        end: Position;
    }
}

/** The suggestion */
export interface AutocompleteSuggestion {
    //Either text or snippet is required

    text: string;
    snippet: string;
    type: string;

    replacementPrefix?: string;

    rightLabel?: string;
    rightLabelHTML?: string;
    leftLabel?: string;
    description?: string;
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
                stdio: string) {

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
                        this.handleResponse(response);
                    });

                resolve(socket);
            }).listen(port, () => {
                // Start the child java process
                child_process.spawn(javaExecutablePath, args, { 'stdio': stdio });
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
            var request: Request = { requestId: requestId };

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
        var todo = this.requestCallbacks[response.requestId];

        this.requestCallbacks[response.requestId] = null;

        if (!todo)
            console.error('No callback registered for request id ' + response.requestId);
        else
            todo(response);
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

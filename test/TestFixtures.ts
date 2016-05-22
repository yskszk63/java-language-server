import {JavacServicesHolder} from '../lib/JavacServices';

export const EMPTY_JAVAC = new JavacServicesHolder('.', '.', message => console.error(message)).getJavac(['test'], [], 'target');

process.on('unhandledRejection', function(reason, p){
    if (reason instanceof Error)
        console.log(reason.stack);
    else
        console.log(reason);
    // application specific logging here
});
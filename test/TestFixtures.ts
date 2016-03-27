import {JavacFactory} from '../lib/JavacServices';

export const EMPTY_JAVAC = new JavacFactory('.', '.', message => console.log(message)).forConfig([], [], 'target');

process.on('unhandledRejection', function(reason, p){
    if (reason instanceof Error)
        console.log(reason.stack);
    else
        console.log(reason);
    // application specific logging here
});
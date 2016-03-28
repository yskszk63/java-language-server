import {JavacFactory} from '../lib/JavacServices';

export const EMPTY_JAVAC = new JavacFactory('.', '.', message => console.error(message)).forConfig(['test'], [], 'target');

process.on('unhandledRejection', function(reason, p){
    if (reason instanceof Error)
        console.log(reason.stack);
    else
        console.log(reason);
    // application specific logging here
});
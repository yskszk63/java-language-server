import Java = require('../lib/JavacServices');

export const EMPTY_JAVAC = Java.provideJavac('.', '.', message => console.error(message));

process.on('unhandledRejection', function(reason, p){
    if (reason instanceof Error)
        console.log(reason.stack);
    else
        console.log(reason);
    // application specific logging here
});
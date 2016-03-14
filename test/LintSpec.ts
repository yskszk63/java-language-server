import {EMPTY_JAVAC} from './TestFixtures';
import * as assert from 'assert';

describe('lint', () => {
    it('should report a syntax error', done => {
        EMPTY_JAVAC.then(javac => {
            let path = 'test/examples/SyntaxError.java';
            
            return javac.lint({ path }).then(result => {
                let ms = messages(result.messages);
                
                assert(ms.length > 0, `${ms} is empty`);

                done();
            });
        });
    });

    it('should report a type error', done => {
        EMPTY_JAVAC.then(javac => {
            let path = 'test/examples/TypeError.java';
            
            return javac.lint({ path }).then(result => {
                let ms = messages(result.messages);
                
                assert(ms.length > 0, `${ms} is empty`);

                done();
            });
        });
    });
});

function messages<T>(messages: { [uri: string]: T[] }): T[] {
    var acc = [];
    
    for (let k of Object.keys(messages))
        acc.push(...messages[k]);
        
    return acc;
}

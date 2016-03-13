import {EMPTY_JAVAC} from './TestFixtures';
import * as assert from 'assert';

describe('lint', () => {
    it('should report a syntax error', done => {
        EMPTY_JAVAC.then(javac => {
            return javac.lint({
                path: 'test/examples/SyntaxError.java'
            }).then(result => {
                assert(result.messages.length > 0, `${result.messages} is empty`);

                done();
            });
        });
    });

    it('should report a type error', done => {
        EMPTY_JAVAC.then(javac => {
            return javac.lint({
                path: 'test/examples/TypeError.java'
            }).then(result => {
                assert(result.messages.length > 0, `${result.messages} is empty`);

                done();
            });
        });
    });
});

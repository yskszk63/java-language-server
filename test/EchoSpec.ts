import {EMPTY_JAVAC} from './TestFixtures';
import * as assert from 'assert';

describe('echo', () => {
    it('should load javacServices', done => {
        EMPTY_JAVAC.then(javac => {
            assert.notEqual(javac, null);

            done();
        });
    });

    // Check that most basic message works
    it('should return a message', done => {
        EMPTY_JAVAC.then(javac => {
            javac.echo('Hello world!').then(result => {
                assert.equal(result, 'Hello world!');

                done();
            });
        });
    });

    // If you accidentally close System.out on the first message, this breaks
    it('should return a message again', done => {
        EMPTY_JAVAC.then(javac => {
            javac.echo('Hello again!').then(result => {
                assert.equal(result, 'Hello again!');

                done();
            });
        });
    });
});

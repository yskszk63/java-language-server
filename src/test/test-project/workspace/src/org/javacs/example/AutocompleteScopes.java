package org.javacs.example;

public class AutocompleteScopes {
    static void outerStaticMethod() { }
    void outerMethods() { }

    static class Super {
        static void inheritedStaticMethod() { }
        void inheritedMethods() { }
    }

    static class StaticSub extends Super {
        void test(String arguments) {
            int localVariables;
            s;
            // Locals
            // YES: localVariables, arguments
            //
            // Static methods in enclosing scopes
            // YES: testStatic
            // YES: outerStaticMethod
            //
            // Virtual methods in enclosing scopes
            // NO: testInner
            // YES: test
            // NO: outerMethods
            //
            // Inherited static methods
            // YES: inheritedStaticMethod
            //
            // Inherited virtual methods
            // YES: inheritedMethods
            //
            // this/super in enclosing scopes
            // YES: this, super
            // YES: StaticSub.this, StaticSub.super
            StaticSub.s;
            // NO: AutocompleteScopes.this, AutocompleteScopes.super
            AutocompleteScopes.s;
            // NO: Super.this, Super.super
            Super.s;

            new Object() {
                void testInner() {
                    s;
                    // Locals
                    // YES: localVariables, arguments
                    //
                    // Static methods in enclosing scopes
                    // YES: testStatic
                    // YES: outerStaticMethod
                    //
                    // Virtual methods in enclosing scopes
                    // YES: testInner
                    // YES: test
                    // NO: outerMethods
                    //
                    // Inherited static methods
                    // YES: inheritedStaticMethod
                    //
                    // Inherited virtual methods
                    // YES: inheritedMethods
                    //
                    // this/super in enclosing scopes
                    // YES: this, super
                    // YES: StaticSub.this, StaticSub.super
                    StaticSub.s;
                    // NO: AutocompleteScopes.this, AutocompleteScopes.super
                    AutocompleteScopes.s;
                    // NO: Super.this, Super.super
                    Super.s;
                }
            };
        }

        static void testStatic(String arguments) {
            int localVariables;
            s;
            // Locals
            // YES: localVariables, arguments
            //
            // Static methods in enclosing scopes
            // YES: testStatic
            // YES: outerStaticMethod
            //
            // Virtual methods in enclosing scopes
            // NO: testInner
            // NO: test
            // NO: outerMethods
            //
            // Inherited static methods
            // YES: inheritedStaticMethod
            //
            // Inherited virtual methods
            // NO: inheritedMethods
            //
            // this/super in enclosing scopes
            // NO: this, super
            // NO: StaticSub.this, StaticSub.super
            StaticSub.s;
            // NO: AutocompleteScopes.this, AutocompleteScopes.super
            AutocompleteScopes.s;
            // NO: Super.this, Super.super
            Super.s;

            new Object() {
                void testInner() {
                    s;
                    // Locals
                    // YES: localVariables, arguments
                    //
                    // Static methods in enclosing scopes
                    // YES: testStatic
                    // YES: outerStaticMethod
                    //
                    // Virtual methods in enclosing scopes
                    // YES: testInner
                    // NO: test
                    // NO: outerMethods
                    //
                    // Inherited static methods
                    // YES: inheritedStaticMethod
                    //
                    // Inherited virtual methods
                    // NO: inheritedMethods
                    //
                    // this/super in enclosing scopes
                    // YES: this, super
                    // NO: StaticSub.this, StaticSub.super
                    StaticSub.s;
                    // NO: AutocompleteScopes.this, AutocompleteScopes.super
                    AutocompleteScopes.s;
                    // NO: Super.this, Super.super
                    Super.s;
                }
            };
        }
    }

    class Sub extends Super {
        void test(String arguments) {
            int localVariables;
            s;
            // Locals
            // YES: localVariables, arguments
            //
            // Methods in enclosing scopes
            // NO: testInner
            // YES: test
            // YES: outerMethods, outerStaticMethod
            //
            // Inherited methods
            // YES: inheritedMethods, inheritedStaticMethod
            //
            // this/super in enclosing scopes
            // YES: this, super
            // YES: Sub.this, Sub.super
            Sub.s;
            // YES: AutocompleteScopes.this, AutocompleteScopes.super
            AutocompleteScopes.s;
            // NO: Super.this, Super.super
            Super.s;

            new Object() {
                void testInner() {
                    s;
                    // Locals
                    // YES: localVariables, arguments
                    //
                    // Methods in enclosing scopes
                    // YES: testInner
                    // YES: test
                    // YES: outerMethods, outerStaticMethod
                    //
                    // Inherited methods
                    // YES: inheritedMethods, inheritedStaticMethod
                    //
                    // this/super in enclosing scopes
                    // YES: this, super
                    // YES: Sub.this, Sub.super
                    Sub.s;
                    // YES: AutocompleteScopes.this, AutocompleteScopes.super
                    AutocompleteScopes.s;
                    // NO: Super.this, Super.super
                    Super.s;
                }
            };
        }
    }
}
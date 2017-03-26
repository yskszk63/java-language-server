package org.javacs.example;

public class AutocompleteScopes {
    static void outerStaticMethod() { }
    void outerMethod() { }

    static class Super {
        static void inheritedStaticMethod() { }
        void inheritedMethod() { }
    }

    static class StaticSub extends Super {
        void test(String argument) {
            int localVariable;
            m;
            // Locals
            // YES: localVariable, argument
            //
            // Static methods in enclosing scopes
            // YES: testStatic
            // YES: outerStaticMethod
            //
            // Virtual methods in enclosing scopes
            // NO: testInner
            // YES: test
            // NO: outerMethod
            //
            // Inherited static methods
            // YES: inheritedStaticMethod
            //
            // Inherited virtual methods
            // YES: inheritedMethod
            //
            // this/super in enclosing scopes
            // YES: this, super
            // YES: StaticSub.this, StaticSub.super
            StaticSub.m;
            // NO: AutocompleteScopes.this, AutocompleteScopes.super
            AutocompleteScopes.m;
            // NO: Super.this, Super.super
            Super.m;

            new Object() {
                void testInner() {
                    m;
                    // Locals
                    // YES: localVariable, argument
                    //
                    // Static methods in enclosing scopes
                    // YES: testStatic
                    // YES: outerStaticMethod
                    //
                    // Virtual methods in enclosing scopes
                    // YES: testInner
                    // YES: test
                    // NO: outerMethod
                    //
                    // Inherited static methods
                    // YES: inheritedStaticMethod
                    //
                    // Inherited virtual methods
                    // YES: inheritedMethod
                    //
                    // this/super in enclosing scopes
                    // YES: this, super
                    // YES: StaticSub.this, StaticSub.super
                    StaticSub.m;
                    // NO: AutocompleteScopes.this, AutocompleteScopes.super
                    AutocompleteScopes.m;
                    // NO: Super.this, Super.super
                    Super.m;
                }
            };
        }

        static void testStatic(String argument) {
            int localVariable;
            m;
            // Locals
            // YES: localVariable, argument
            //
            // Static methods in enclosing scopes
            // YES: testStatic
            // YES: outerStaticMethod
            //
            // Virtual methods in enclosing scopes
            // NO: testInner
            // NO: test
            // NO: outerMethod
            //
            // Inherited static methods
            // YES: inheritedStaticMethod
            //
            // Inherited virtual methods
            // NO: inheritedMethod
            //
            // this/super in enclosing scopes
            // YES: this, super
            // NO: StaticSub.this, StaticSub.super
            StaticSub.m;
            // NO: AutocompleteScopes.this, AutocompleteScopes.super
            AutocompleteScopes.m;
            // NO: Super.this, Super.super
            Super.m;

            new Object() {
                void testInner() {
                    m;
                    // Locals
                    // YES: localVariable, argument
                    //
                    // Static methods in enclosing scopes
                    // YES: testStatic
                    // YES: outerStaticMethod
                    //
                    // Virtual methods in enclosing scopes
                    // YES: testInner
                    // NO: test
                    // NO: outerMethod
                    //
                    // Inherited static methods
                    // YES: inheritedStaticMethod
                    //
                    // Inherited virtual methods
                    // NO: inheritedMethod
                    //
                    // this/super in enclosing scopes
                    // YES: this, super
                    // NO: StaticSub.this, StaticSub.super
                    StaticSub.m;
                    // NO: AutocompleteScopes.this, AutocompleteScopes.super
                    AutocompleteScopes.m;
                    // NO: Super.this, Super.super
                    Super.m;
                }
            };
        }
    }

    class Sub extends Super {
        void test(String argument) {
            int localVariable;
            m;
            // Locals
            // YES: localVariable, argument
            //
            // Methods in enclosing scopes
            // NO: testInner
            // YES: test
            // YES: outerMethod, outerStaticMethod
            //
            // Inherited methods
            // YES: inheritedMethod, inheritedStaticMethod
            //
            // this/super in enclosing scopes
            // YES: this, super
            // YES: Sub.this, Sub.super
            Sub.m;
            // YES: AutocompleteScopes.this, AutocompleteScopes.super
            AutocompleteScopes.m;
            // NO: Super.this, Super.super
            Super.m;

            new Object() {
                void testInner() {
                    m;
                    // Locals
                    // YES: localVariable, argument
                    //
                    // Methods in enclosing scopes
                    // YES: testInner
                    // YES: test
                    // YES: outerMethod, outerStaticMethod
                    //
                    // Inherited methods
                    // YES: inheritedMethod, inheritedStaticMethod
                    //
                    // this/super in enclosing scopes
                    // YES: this, super
                    // YES: Sub.this, Sub.super
                    Sub.m;
                    // YES: AutocompleteScopes.this, AutocompleteScopes.super
                    AutocompleteScopes.m;
                    // NO: Super.this, Super.super
                    Super.m;
                }
            };
        }
    }
}
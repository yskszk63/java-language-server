package simple;

class UnderlineMutable {
    void method(int param) {
        param = 2;
        
        var local = 3;
        local = 4;
    }
}
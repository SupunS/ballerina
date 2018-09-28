type Person1 abstract object {
    public int age = 10;
    public string name = "sample name";
    
    public function getName() returns string;
};

type Employee1 abstract object {
    public float salary;
    
    public function getSalary() returns float; 
};

type Manager1 object {
    *Person1;

    string dpt = "HR";

    *Employee1;

    function getName() returns string {
        return self.name + " from inner function";
    }
};

function Manager1::getSalary() returns float {
    return self.salary;
}

public function testSimpleObjectTypeReference() returns (int, string, float, string) {
    Manager1 mgr = new Manager1();
    return (mgr.age, mgr.getName(), mgr.getSalary(), mgr.dpt);
}

type Manager2 object {
    *Person1;

    string dpt = "HR";

    *Employee1;

    function getName() returns string {
        return self.name + " from inner function";
    }

    new(age=20) {
        name = "John";
        salary = 1000.0;
    }
};

function Manager2::getSalary() returns float {
    return self.salary;
}

public function testInitTypeReferenceObjectWithNew() returns (int, string, float, string) {
    Manager2 mgr = new Manager2();
    return (mgr.age, mgr.getName(), mgr.getSalary(), mgr.dpt);
}

type Manager3 object {
    string dpt = "HR";

    *Employee2;

    new(age=20) {
        salary = 2500.0;
    } 
};

type Employee2 abstract object {
    public float salary;
    *Person1;
    
    public function getSalary() returns float; 
};

function Manager3::getName() returns string {
    return self.name + " from outer function";
}

function Manager3::getSalary() returns float {
    return self.salary;
}

public function testObjectWithChainedTypeReferences() returns (int, string, float, string) {
    Manager3 mgr = new Manager3();
    mgr.name = "John";
    return (mgr.age, mgr.getName(), mgr.getSalary(), mgr.dpt);
}

type Person1 abstract object {
    public int age = 10;
    public string name = "sample name";
};

type Employee1 object {
    public float salary;
};

type Manager1 object {
    *Person1;

    string dpt = "HR";

    // refering a non-abstarct object
    *Employee1;
};

type EmployeeWithSalary abstract object {
    public float salary;
};

type AnotherEmployeeWithSalary abstract object {
    public int salary;
};

type ManagerWithTwoSalaries object {
    *Person1;

    string dpt = "HR";
    *EmployeeWithSalary;
    *AnotherEmployeeWithSalary;
};

// Circular references
type A abstract object {
    *B;
};

type B abstract object {
    *C;
};

type C abstract object {
    *D;
    *E;
};

type D abstract object {
    *A;
};

type E abstract object {
    *C;
};

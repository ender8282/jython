// Copyright (c) Corporation for National Research Initiatives
package org.python.core;
import java.util.Hashtable;

/**
 * A python class instance.
 */

public class PyInstance extends PyObject
{
    // xxx doc, final name
    public transient PyClass instclass;

    // xxx
    public PyObject fastGetClass() {
        return instclass;
    }

    /**
       The namespace of this instance.  Contains all instance attributes.
    **/
    public PyObject __dict__;

    /* Override serialization behavior */
    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException
    {
        in.defaultReadObject();

        String module = in.readUTF();
        String name = in.readUTF();

        /* Check for types and missing members here */
        //System.out.println("module: "+module+", "+name);
        PyObject mod = imp.importName(module.intern(), false);
        PyClass pyc = (PyClass)mod.__getattr__(name.intern());

        instclass = pyc;
    }

    private void writeObject(java.io.ObjectOutputStream out)
        throws java.io.IOException
    {
        //System.out.println("writing: "+getClass().getName());
        out.defaultWriteObject();
        PyObject name = instclass.__findattr__("__module__");
        if (!(name instanceof PyString) || name == Py.None) {
            throw Py.ValueError("Can't find module for class: "+
                                instclass.__name__);
        }
        out.writeUTF(name.toString());
        name = instclass.__findattr__("__name__");
        if (!(name instanceof PyString) || name == Py.None) {
            throw Py.ValueError("Can't find module for class with no name");
        }

        out.writeUTF(name.toString());
    }


    /**
       Returns a new
    **/

    public PyInstance(PyClass iclass, PyObject dict) {
        instclass = iclass;
        if (dict == Py.None) {
            dict = new PyStringMap();
        }
        __dict__ = dict;
    }

    public PyInstance(PyClass iclass) {
        this(iclass, new PyStringMap());
    }

    public PyInstance() {}

    private static Hashtable primitiveMap;

    public Object __tojava__(Class c) {
        if (c.isInstance(this))
            return this;

        if (instclass.__tojava__ != null) {
            // try {
            PyObject ret = instclass.__tojava__.__call__(this, PyType.fromClass(c));

            if (ret == Py.None)
                return Py.NoConversion;
            if (ret != this)
                return ret.__tojava__(c);
            /*} catch (PyException exc) {
              System.err.println("Error in __tojava__ method");
              Py.printException(exc);
              }*/
        }
        return Py.NoConversion;
    }

    public void __init__(PyObject[] args, String[] keywords) {
        // Invoke our own init function
        PyObject init = instclass.lookup("__init__", true);
        PyObject ret = null;
        if (init != null) {
            ret = init.__call__(this, args, keywords);
        }
        if (ret == null) {
            if (args.length != 0) {
                init = instclass.lookup("__init__", false);
                if (init != null) {
                    ret = init.__call__(this, args, keywords);
                } else {
                    throw Py.TypeError("this constructor takes no arguments");
                }
            }
        }
        else if (ret != Py.None) {
            throw Py.TypeError("__init__() should return None");
        }
    }

    public PyObject __jfindattr__(String name) {
        //System.err.println("jfinding: "+name);
        return __findattr__(name, true);
    }

    public PyObject __findattr_ex__(String name) {
        return __findattr_ex__(name, false);
    }

    public PyObject __findattr__(String name, boolean stopAtJava) {
        try {
            return __findattr_ex__(name, stopAtJava);
        } catch (PyException exc) {
            if (Py.matchException(exc, Py.AttributeError)) return null;
            throw exc;
        }
    }

    protected PyObject __findattr_ex__(String name, boolean stopAtJava) {
        PyObject result = ifindlocal(name);
        if (result != null)
            return result;
        // it wasn't found in the instance, try the class
        PyObject[] result2 = instclass.lookupGivingClass(name, stopAtJava);
        if (result2[0] != null)
            // xxx do we need to use result2[1] (wherefound) for java cases for backw comp?
            return result2[0].__get__(this, instclass);
            // xxx do we need to use
        return ifindfunction(name);
    }

    protected PyObject ifindlocal(String name) {
        if (name == "__dict__") return __dict__;
        if (name == "__class__") return instclass;
        if (__dict__ == null) return null;

        return __dict__.__finditem__(name);
    }

    protected PyObject ifindclass(String name, boolean stopAtJava) {
        return instclass.lookup(name, stopAtJava);
    }

    protected PyObject ifindfunction(String name) {
        PyObject getter = instclass.__getattr__;
        if (getter == null)
            return null;

        return getter.__call__(this, new PyString(name));
    }

    public boolean isCallable() {
        return __findattr__("__call__") != null;
    }

    @Override
    public boolean isIndex() {
        return __findattr__("__index__") != null;
    }

    public PyObject invoke(String name) {
        PyObject f = ifindlocal(name);
        if (f == null) {
            f = ifindclass(name, false);
            if (f != null) {
                if (f instanceof PyFunction) {
                    return f.__call__(this);
                } else {
                    f = f.__get__(this, instclass);
                }
            }
        }
        if (f == null) f = ifindfunction(name);
        if (f == null) noAttributeError(name);
        return f.__call__();
    }

    public PyObject invoke(String name, PyObject arg1) {
        PyObject f = ifindlocal(name);
        if (f == null) {
            f = ifindclass(name, false);
            if (f != null) {
                if (f instanceof PyFunction) {
                    return f.__call__(this, arg1);
                } else {
                    f = f.__get__(this, instclass);
                }
            }
        }
        if (f == null) f = ifindfunction(name);
        if (f == null) noAttributeError(name);
        return f.__call__(arg1);
    }

    public PyObject invoke(String name, PyObject arg1, PyObject arg2) {
        PyObject f = ifindlocal(name);
        if (f == null) {
            f = ifindclass(name, false);
            if (f != null) {
                if (f instanceof PyFunction) {
                    return f.__call__(this, arg1, arg2);
                } else {
                    f = f.__get__(this, instclass);
                }
            }
        }
        if (f == null) f = ifindfunction(name);
        if (f == null) noAttributeError(name);
        return f.__call__(arg1, arg2);
    }

    public void noAttributeError(String name) {
        throw Py.AttributeError(String.format("%.50s instance has no attribute '%.400s'",
                                              instclass.__name__, name));
    }


    public void __setattr__(String name, PyObject value) {
        if (name == "__class__") {
            if (value instanceof PyClass) {
                instclass = (PyClass)value;
            } else {
                throw Py.TypeError("__class__ must be set to a class");
            }
            return;
        } else if (name == "__dict__") {
            __dict__ = value;
            return;
        }

        PyObject setter = instclass.__setattr__;
        if (setter != null) {
            setter.__call__(this, new PyString(name), value);
        } else {
            __dict__.__setitem__(name, value);
        }
    }

    protected void noField(String name, PyObject value) {
        __dict__.__setitem__(name, value);
    }

    protected void unassignableField(String name, PyObject value) {
        __dict__.__setitem__(name, value);
    }

    public void __delattr__(String name) {
        PyObject deller = instclass.__delattr__;
        if (deller != null) {
            deller.__call__(this, new PyString(name));
        } else {
            try {
                __dict__.__delitem__(name);
            } catch (PyException exc) {
                if (Py.matchException(exc, Py.KeyError))
                    throw Py.AttributeError("class " + instclass.__name__ +
                                        " has no attribute '" + name + "'");
            };
        }
    }

    public PyObject invoke_ex(String name, PyObject[] args, String[] keywords)
    {
        PyObject meth = __findattr__(name);
        if (meth == null)
            return null;
        return meth.__call__(args, keywords);
    }

    public PyObject invoke_ex(String name) {
        PyObject meth = __findattr__(name);
        if (meth == null)
            return null;
        return meth.__call__();
    }
    public PyObject invoke_ex(String name, PyObject arg1) {
        PyObject meth = __findattr__(name);
        if (meth == null)
            return null;
        return meth.__call__(arg1);
    }
    public PyObject invoke_ex(String name, PyObject arg1, PyObject arg2) {
        PyObject meth = __findattr__(name);
        if (meth == null)
            return null;
        return meth.__call__(arg1, arg2);
    }

    public PyObject __call__(PyObject args[], String keywords[]) {
        ThreadState ts = Py.getThreadState();
        if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
            throw Py.RuntimeError("maximum __call__ recursion depth exceeded");
        try {
            return invoke("__call__", args, keywords);
        } finally {
            --ts.recursion_depth;
        }
    }

    public PyString __repr__() {
        PyObject ret = invoke_ex("__repr__");
        if (ret == null) {
            return makeDefaultRepr();
        }
        if (!(ret instanceof PyString))
            throw Py.TypeError("__repr__ method must return a string");
        return (PyString)ret;
    }

    /**
     * If a class doesn't define a __repr__ method of its own, the return
     * value from this method is used.
     */
    protected PyString makeDefaultRepr() {
        PyObject mod = instclass.__dict__.__finditem__("__module__");
        String smod;
        if(mod == Py.None) {
            smod = "";
        } else {
            if(mod == null || !(mod instanceof PyString)) {
                smod = "<unknown>.";
            } else {
                smod = ((PyString)mod).toString() + '.';
            }
        }
        return new PyString("<" + smod + instclass.__name__ + " instance at " +
                            Py.idstr(this) + ">");
    }

    public PyString __str__() {
        PyObject ret = invoke_ex("__str__");
        if (ret == null)
            return __repr__();
        if (!(ret instanceof PyString))
            throw Py.TypeError("__str__ method must return a string");
        return (PyString)ret;
    }

    public PyUnicode __unicode__() {
        PyObject ret = invoke_ex("__unicode__");
        if(ret == null) {
            return super.__unicode__();
        } else if(ret instanceof PyUnicode) {
            return (PyUnicode)ret;
        } else if(ret instanceof PyString) {
            return new PyUnicode((PyString)ret);
        } else {
            throw Py.TypeError("__unicode__ must return unicode or str");
        }
    }

    public int hashCode() {
        PyObject ret;
        ret = invoke_ex("__hash__");
        if (ret == null) {
            if (__findattr__("__eq__") != null || __findattr__("__cmp__") != null) {
                throw Py.TypeError("unhashable instance");
            }
            return super.hashCode();
        }
        if (ret instanceof PyInteger) {
            return ((PyInteger)ret).getValue();
        }
        else if (ret instanceof PyLong) {
            return ((PyLong)ret).hashCode();
        }
        throw Py.TypeError("__hash__() must really return int" + ret.getType() );
    }

    // special case: does all the work
    public int __cmp__(PyObject other) {
        PyObject[] coerced = this._coerce(other);
        PyObject v;
        PyObject w;
        PyObject ret = null;
        if (coerced != null) {
            v = coerced[0];
            w = coerced[1];
            if (!(v instanceof PyInstance) && !(w instanceof PyInstance)) {
                return v._cmp(w);
            }
        } else {
            v = this;
            w = other;
        }
        if (v instanceof PyInstance) {
            ret = ((PyInstance)v).invoke_ex("__cmp__",w);
            if (ret != null) {
                if (ret instanceof PyInteger) {
                    int result = ((PyInteger)ret).getValue();
                    return result < 0 ? -1 : result > 0 ? 1 : 0;
                }
                throw Py.TypeError("__cmp__() must return int");
            }
        }
        if (w instanceof PyInstance) {
            ret = ((PyInstance)w).invoke_ex("__cmp__",v);
            if (ret != null) {
                if (ret instanceof PyInteger) {
                    int result = ((PyInteger)ret).getValue();
                    return -(result < 0 ? -1 : result > 0 ? 1 : 0);
                }
                throw Py.TypeError("__cmp__() must return int");
            }

        }
        return -2;
    }

    private PyObject invoke_ex_richcmp(String name, PyObject o) {
        PyObject ret = invoke_ex(name, o);
        if (ret == Py.NotImplemented)
            return null;
        return ret;
    }

    public PyObject __lt__(PyObject o) {
        return invoke_ex_richcmp("__lt__", o);
    }

    public PyObject __le__(PyObject o) {
        return invoke_ex_richcmp("__le__", o);
    }

    public PyObject __gt__(PyObject o) {
        return invoke_ex_richcmp("__gt__", o);
    }

    public PyObject __ge__(PyObject o) {
        return invoke_ex_richcmp("__ge__", o);
    }

    public PyObject __eq__(PyObject o) {
        return invoke_ex_richcmp("__eq__", o);
    }

    public PyObject __ne__(PyObject o) {
        return invoke_ex_richcmp("__ne__", o);
    }

    public boolean __nonzero__() {
        PyObject meth = null;
        try {
            meth = __findattr__("__nonzero__");
        } catch (PyException exc) { }

        if (meth == null) {
            try {
                meth = __findattr__("__len__");
            } catch (PyException exc) { }
            if (meth == null)
                return true;
        }

        PyObject ret = meth.__call__();
        return ret.__nonzero__();
    }

    public int __len__() {
        PyObject ret = invoke("__len__");
        if (ret instanceof PyInteger)
            return ((PyInteger)ret).getValue();
        throw Py.TypeError("__len__() should return an int");
    }

    public PyObject __finditem__(int key) {
        return __finditem__(new PyInteger(key));
    }

    private PyObject trySlice(String name, PyObject start, PyObject stop) {
        return trySlice(name, start, stop, null);
    }

    private PyObject trySlice(String name, PyObject start, PyObject stop, PyObject extraArg) {
        PyObject func = __findattr__(name);
        if (func == null) {
            return null;
        }

        PyObject[] indices = PySlice.indices2(this, start, stop);
        start = indices[0];
        stop = indices[1];

        if (extraArg == null) {
            return func.__call__(start, stop);
        } else {
            return func.__call__(start, stop, extraArg);
        }
    }

    public PyObject __finditem__(PyObject key) {
        try {
            return invoke("__getitem__", key);
        } catch (PyException e) {
            if (Py.matchException(e, Py.IndexError))
                return null;
            if (Py.matchException(e, Py.KeyError))
                return null;
            throw e;
        }
    }

    public PyObject __getitem__(PyObject key) {
        return invoke("__getitem__", key);
    }

    public void __setitem__(PyObject key, PyObject value) {
        invoke("__setitem__", key, value);
    }

    public void __delitem__(PyObject key) {
        invoke("__delitem__", key);
    }

    public PyObject __getslice__(PyObject start, PyObject stop, PyObject step) {
        if (step != null) {
            return __getitem__(new PySlice(start, stop, step));
        }
        PyObject ret = trySlice("__getslice__", start, stop);
        if (ret != null) {
            return ret;
        }
        return super.__getslice__(start, stop, step);
    }

    public void __setslice__(PyObject start, PyObject stop, PyObject step, PyObject value) {
        if (step != null) {
            __setitem__(new PySlice(start, stop, step), value);
        } else if (trySlice("__setslice__", start, stop, value) == null) {
            super.__setslice__(start, stop, step, value);
        }
    }

    public void __delslice__(PyObject start, PyObject stop, PyObject step) {
        if (step != null) {
            __delitem__(new PySlice(start, stop, step));
        } else if (trySlice("__delslice__", start, stop) == null) {
            super.__delslice__(start, stop, step);
        }
    }

    public PyObject __iter__() {
        PyObject func = __findattr__("__iter__");
        if (func != null)
            return func.__call__();
        func = __findattr__("__getitem__");
        if (func == null)
            return super.__iter__();
        return new PySequenceIter(this);
    }

    public PyObject __iternext__() {
        PyObject func = __findattr__("next");
        if (func != null) {
            try {
                return func.__call__();
            } catch (PyException exc) {
                if (Py.matchException(exc, Py.StopIteration))
                    return null;
                throw exc;
            }
        }
        throw Py.TypeError("instance has no next() method");
    }

    public boolean __contains__(PyObject o) {
        PyObject func = __findattr__("__contains__");
        if (func == null)
           return super.__contains__(o);
        PyObject ret = func.__call__(o);
        return ret.__nonzero__();
    }

    //Begin the numeric methods here
    public Object __coerce_ex__(PyObject o) {
        PyObject ret = invoke_ex("__coerce__", o);
        if (ret == null || ret == Py.None)
            return ret;
        if (!(ret instanceof PyTuple))
            throw Py.TypeError("coercion should return None or 2-tuple");
        return ((PyTuple)ret).getArray();
    }

    /**
     * Implements the __index__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __index__() {
        PyObject ret;
        try {
            ret = invoke("__index__");
        } catch (PyException pye) {
            if (!Py.matchException(pye, Py.AttributeError)) {
                throw pye;
            }
            throw Py.TypeError("object cannot be interpreted as an index");
        }
        if (ret instanceof PyInteger || ret instanceof PyLong) {
            return ret;
        }
        throw Py.TypeError(String.format("__index__ returned non-(int,long) (type %s)",
                                         ret.getType().fastGetName()));
    }


    // Generated by make_binops.py

    // Unary ops

    /**
     * Implements the __hex__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyString __hex__() {
        PyObject ret = invoke("__hex__");
        if (ret instanceof PyString)
            return (PyString)ret;
        throw Py.TypeError("__hex__() should return a string");
    }

    /**
     * Implements the __oct__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyString __oct__() {
        PyObject ret = invoke("__oct__");
        if (ret instanceof PyString)
            return (PyString)ret;
        throw Py.TypeError("__oct__() should return a string");
    }

    /**
     * Implements the __int__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __int__() {
        PyObject ret = invoke("__int__");
        if (ret instanceof PyLong || ret instanceof PyInteger)
            return ret;
        throw Py.TypeError("__int__() should return a int");
    }

    /**
     * Implements the __float__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyFloat __float__() {
        PyObject ret = invoke("__float__");
        if (ret instanceof PyFloat)
            return (PyFloat)ret;
        throw Py.TypeError("__float__() should return a float");
    }

    /**
     * Implements the __long__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __long__() {
        PyObject ret = invoke("__long__");
        if (ret instanceof PyLong || ret instanceof PyInteger)
            return ret;
        throw Py.TypeError("__long__() should return a long");
    }

    /**
     * Implements the __complex__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyComplex __complex__() {
        PyObject ret = invoke("__complex__");
        if (ret instanceof PyComplex)
            return (PyComplex)ret;
        throw Py.TypeError("__complex__() should return a complex");
    }

    /**
     * Implements the __pos__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __pos__() {
        return invoke("__pos__");
    }

    /**
     * Implements the __neg__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __neg__() {
        return invoke("__neg__");
    }

    /**
     * Implements the __abs__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __abs__() {
        return invoke("__abs__");
    }

    /**
     * Implements the __invert__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __invert__() {
        return invoke("__invert__");
    }

    // Binary ops

    /**
     * Implements the __add__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __add__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__add__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__add__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._add(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __radd__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __radd__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__radd__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__radd__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._add(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __iadd__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __iadd__(PyObject o) {
        PyObject ret = invoke_ex("__iadd__", o);
        if (ret != null)
            return ret;
        return super.__iadd__(o);
    }

    /**
     * Implements the __sub__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __sub__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__sub__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__sub__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._sub(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rsub__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rsub__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rsub__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rsub__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._sub(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __isub__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __isub__(PyObject o) {
        PyObject ret = invoke_ex("__isub__", o);
        if (ret != null)
            return ret;
        return super.__isub__(o);
    }

    /**
     * Implements the __mul__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __mul__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__mul__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__mul__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._mul(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rmul__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rmul__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rmul__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rmul__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._mul(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __imul__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __imul__(PyObject o) {
        PyObject ret = invoke_ex("__imul__", o);
        if (ret != null)
            return ret;
        return super.__imul__(o);
    }

    /**
     * Implements the __div__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __div__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__div__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__div__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._div(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rdiv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rdiv__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rdiv__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rdiv__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._div(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __idiv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __idiv__(PyObject o) {
        PyObject ret = invoke_ex("__idiv__", o);
        if (ret != null)
            return ret;
        return super.__idiv__(o);
    }

    /**
     * Implements the __floordiv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __floordiv__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__floordiv__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__floordiv__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._floordiv(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rfloordiv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rfloordiv__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rfloordiv__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rfloordiv__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._floordiv(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __ifloordiv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __ifloordiv__(PyObject o) {
        PyObject ret = invoke_ex("__ifloordiv__", o);
        if (ret != null)
            return ret;
        return super.__ifloordiv__(o);
    }

    /**
     * Implements the __truediv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __truediv__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__truediv__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__truediv__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._truediv(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rtruediv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rtruediv__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rtruediv__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rtruediv__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._truediv(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __itruediv__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __itruediv__(PyObject o) {
        PyObject ret = invoke_ex("__itruediv__", o);
        if (ret != null)
            return ret;
        return super.__itruediv__(o);
    }

    /**
     * Implements the __mod__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __mod__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__mod__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__mod__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._mod(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rmod__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rmod__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rmod__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rmod__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._mod(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __imod__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __imod__(PyObject o) {
        PyObject ret = invoke_ex("__imod__", o);
        if (ret != null)
            return ret;
        return super.__imod__(o);
    }

    /**
     * Implements the __divmod__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __divmod__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__divmod__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__divmod__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._divmod(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rdivmod__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rdivmod__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rdivmod__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rdivmod__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._divmod(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __pow__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __pow__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__pow__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__pow__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._pow(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rpow__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rpow__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rpow__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rpow__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._pow(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __ipow__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __ipow__(PyObject o) {
        PyObject ret = invoke_ex("__ipow__", o);
        if (ret != null)
            return ret;
        return super.__ipow__(o);
    }

    /**
     * Implements the __lshift__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __lshift__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__lshift__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__lshift__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._lshift(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rlshift__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rlshift__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rlshift__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rlshift__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._lshift(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __ilshift__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __ilshift__(PyObject o) {
        PyObject ret = invoke_ex("__ilshift__", o);
        if (ret != null)
            return ret;
        return super.__ilshift__(o);
    }

    /**
     * Implements the __rshift__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rshift__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rshift__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rshift__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._rshift(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rrshift__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rrshift__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rrshift__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rrshift__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._rshift(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __irshift__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __irshift__(PyObject o) {
        PyObject ret = invoke_ex("__irshift__", o);
        if (ret != null)
            return ret;
        return super.__irshift__(o);
    }

    /**
     * Implements the __and__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __and__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__and__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__and__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._and(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rand__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rand__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rand__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rand__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._and(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __iand__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __iand__(PyObject o) {
        PyObject ret = invoke_ex("__iand__", o);
        if (ret != null)
            return ret;
        return super.__iand__(o);
    }

    /**
     * Implements the __or__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __or__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__or__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__or__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._or(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __ror__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __ror__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__ror__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__ror__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._or(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __ior__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __ior__(PyObject o) {
        PyObject ret = invoke_ex("__ior__", o);
        if (ret != null)
            return ret;
        return super.__ior__(o);
    }

    /**
     * Implements the __xor__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __xor__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__xor__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__xor__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o1._xor(o2);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __rxor__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __rxor__(PyObject o) {
        Object ctmp = __coerce_ex__(o);
        if (ctmp == null || ctmp == Py.None)
            return invoke_ex("__rxor__", o);
        else {
            PyObject o1 = ((PyObject[])ctmp)[0];
            PyObject o2 = ((PyObject[])ctmp)[1];
            if (this == o1) {
                // Prevent recusion if __coerce__ return self
                return invoke_ex("__rxor__", o2);
            }
            else {
                ThreadState ts = Py.getThreadState();
                if (ts.recursion_depth++ > ts.systemState.getrecursionlimit())
                    throw Py.RuntimeError("maximum recursion depth exceeded");
                try {
                    return o2._xor(o1);
                } finally {
                    --ts.recursion_depth;
                }
            }
        }
    }

    /**
     * Implements the __ixor__ method by looking it up
     * in the instance's dictionary and calling it if it is found.
     **/
    public PyObject __ixor__(PyObject o) {
        PyObject ret = invoke_ex("__ixor__", o);
        if (ret != null)
            return ret;
        return super.__ixor__(o);
    }

}

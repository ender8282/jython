// Autogenerated AST node
package org.python.antlr.ast;
import org.python.antlr.PythonTree;
import org.antlr.runtime.Token;
import java.io.DataOutputStream;
import java.io.IOException;

public class FunctionDef extends stmtType {
    public String name;
    public argumentsType args;
    public stmtType[] body;
    public exprType[] decorators;

    public static final String[] _fields = new String[]
    {"name","args","body","decorators"};

    public FunctionDef(PythonTree tree, String name, argumentsType args,
    stmtType[] body, exprType[] decorators) {
        super(tree);
        this.name = name;
        this.args = args;
        this.body = body;
        if (body != null) {
            for(int ibody=0;ibody<body.length;ibody++) {
                addChild(body[ibody]);
            }
        }
        this.decorators = decorators;
        if (decorators != null) {
            for(int idecorators=0;idecorators<decorators.length;idecorators++) {
                addChild(decorators[idecorators]);
            }
        }
    }

    public String toString() {
        return "FunctionDef";
    }

    public <R> R accept(VisitorIF<R> visitor) throws Exception {
        return visitor.visitFunctionDef(this);
    }

    public void traverse(VisitorIF visitor) throws Exception {
        if (args != null)
            args.accept(visitor);
        if (body != null) {
            for (int i = 0; i < body.length; i++) {
                if (body[i] != null)
                    body[i].accept(visitor);
            }
        }
        if (decorators != null) {
            for (int i = 0; i < decorators.length; i++) {
                if (decorators[i] != null)
                    decorators[i].accept(visitor);
            }
        }
    }

    public int getLineno() {
        return getLine();
    }

    public int getCol_offset() {
        return getCharPositionInLine();
    }

}

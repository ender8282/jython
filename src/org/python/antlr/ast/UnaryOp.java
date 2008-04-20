// Autogenerated AST node
package org.python.antlr.ast;
import org.python.antlr.PythonTree;
import org.antlr.runtime.Token;
import java.io.DataOutputStream;
import java.io.IOException;

public class UnaryOp extends exprType {
    public unaryopType op;
    public exprType operand;

    public static final String[] _fields = new String[] {"op","operand"};

    public UnaryOp(Token token, unaryopType op, exprType operand) {
        super(token);
        this.op = op;
        this.operand = operand;
    }

    public UnaryOp(PythonTree tree, unaryopType op, exprType operand) {
        super(tree);
        this.op = op;
        this.operand = operand;
    }

    public String toString() {
        return "UnaryOp";
    }

    public <R> R accept(VisitorIF<R> visitor) throws Exception {
        return visitor.visitUnaryOp(this);
    }

    public void traverse(VisitorIF visitor) throws Exception {
        if (operand != null)
            operand.accept(visitor);
    }

public int getLineno() {
    return getLine() + 1;
}

public int getCol_offset() {
    return getCharPositionInLine();
}

}

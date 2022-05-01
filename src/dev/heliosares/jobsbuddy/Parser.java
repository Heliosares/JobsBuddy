package dev.heliosares.jobsbuddy;

import java.util.HashMap;

//https://stackoverflow.com/questions/3422673/how-to-evaluate-a-math-expression-given-in-string-form
public class Parser {
	public final String equation;

	public Parser(String equation) {
		this.equation = equation;
	}

	int pos = -1, ch;

	private void nextChar() {
		ch = (++pos < equation.length()) ? equation.charAt(pos) : -1;
	}

	private boolean eat(int charToEat) {
		while (ch == ' ')
			nextChar();
		if (ch == charToEat) {
			nextChar();
			return true;
		}
		return false;
	}

	public double parse() {
		pos = -1;
		ch = -1;
		nextChar();
		double x = parseExpression();
		if (pos < equation.length())
			throw new RuntimeException("Unexpected: " + (char) ch);
		return x;
	}

	private HashMap<String, Double> variables = new HashMap<>();

	public void setVariable(String name, double value) {
		variables.put(name, value);
	}

	// Grammar:
	// expression = term | expression `+` term | expression `-` term
	// term = factor | term `*` factor | term `/` factor
	// factor = `+` factor | `-` factor | `(` expression `)` | number
	// | functionName `(` expression `)` | functionName factor
	// | factor `^` factor

	private double parseExpression() {
		double x = parseTerm();
		for (;;) {
			if (eat('+'))
				x += parseTerm(); // addition
			else if (eat('-'))
				x -= parseTerm(); // subtraction
			else
				return x;
		}
	}

	private double parseTerm() {
		double x = parseFactor();
		for (;;) {
			if (eat('*'))
				x *= parseFactor(); // multiplication
			else if (eat('/'))
				x /= parseFactor(); // division
			else
				return x;
		}
	}

	private double parseFactor() {
		if (eat('+'))
			return +parseFactor(); // unary plus
		if (eat('-'))
			return -parseFactor(); // unary minus

		double x;
		int startPos = this.pos;
		if (eat('(')) { // parentheses
			x = parseExpression();
			if (!eat(')'))
				throw new RuntimeException("Missing ')'");
		} else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
			while ((ch >= '0' && ch <= '9') || ch == '.')
				nextChar();
			x = Double.parseDouble(equation.substring(startPos, this.pos));
		} else if (ch >= 'a' && ch <= 'z') { // functions
			while (ch >= 'a' && ch <= 'z')
				nextChar();
			String func = equation.substring(startPos, this.pos);
			if (variables.containsKey(func)) {
				x = variables.get(func);
			} else {
				if (eat('(')) {
					x = parseExpression();
					if (!eat(')'))
						throw new RuntimeException("Missing ')' after argument to " + func);
				} else {
					x = parseFactor();
				}
				if (func.equals("sqrt"))
					x = Math.sqrt(x);
				else if (func.equals("sin"))
					x = Math.sin(Math.toRadians(x));
				else if (func.equals("cos"))
					x = Math.cos(Math.toRadians(x));
				else if (func.equals("tan"))
					x = Math.tan(Math.toRadians(x));
				else
					throw new RuntimeException("Unknown function: " + func);
			}
		} else {
			throw new RuntimeException(
					"Unexpected: '" + (char) ch + "' (" + ch + ") in equation '" + equation + "' index " + pos);
		}

		if (eat('^'))
			x = Math.pow(x, parseFactor()); // exponentiation

		return x;
	}
}

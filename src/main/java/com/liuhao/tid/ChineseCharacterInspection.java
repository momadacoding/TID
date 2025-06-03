package com.liuhao.tid;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ChineseCharacterInspection extends PyInspection {

    @NotNull
    @Override
    public String getShortName() {
        return "ChineseCharacterInspection";
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          @NotNull LocalInspectionToolSession session) {
        return new ChineseCharacterVisitor(holder, isOnTheFly);
    }

    private static class ChineseCharacterVisitor extends PyElementVisitor {
        private final ProblemsHolder myHolder;
        private final boolean isOnTheFly;

        ChineseCharacterVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            myHolder = holder;
            this.isOnTheFly = isOnTheFly;
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            if (containsNotIdComment(node)) {
                return;
            }

            String name = node.getName();
            if (name != null && containsChineseCharacters(name)) {
                myHolder.registerProblem(node, "Chinese characters detected in reference");
            }
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            if (containsNotIdComment(node)) {
                return;
            }

            String name = node.getName();
            if (name != null && containsChineseCharacters(name)) {
                myHolder.registerProblem(node.getNameIdentifier(), "Chinese characters detected in function name");
            }
        }

        @Override
        public void visitPyClass(PyClass node) {
            if (containsNotIdComment(node)) {
                return;
            }

            String name = node.getName();
            if (name != null && containsChineseCharacters(name)) {
                myHolder.registerProblem(node.getNameIdentifier(), "Chinese characters detected in class name");
            }
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
            // Check for "#notid" comment on the same line
            if (containsNotIdComment(node)) {
                return;
            }

            // Skip docstrings - they should be allowed to contain Chinese characters
            if (isDocString(node)) {
                return;
            }

            // Check for Chinese characters in string literals
            String text = node.getStringValue();
            if (containsChineseCharacters(text)) {
                myHolder.registerProblem(node, "Chinese characters detected");
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            // Check variable names for Chinese characters
            if (containsNotIdComment(node)) {
                return;
            }

            String name = node.getName();
            if (name != null && containsChineseCharacters(name)) {
                myHolder.registerProblem(node, "Chinese characters detected in identifier");
            }
        }

        private boolean containsNotIdComment(PsiElement element) {
            try {
                // Get the document
                PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element.getProject());
                Document document = documentManager.getDocument(element.getContainingFile());
                if (document == null) return false;

                // Find the statement that contains this element
                PsiElement statementElement = element;
                while (statementElement != null && !(statementElement instanceof PyStatement)) {
                    statementElement = statementElement.getParent();
                }
                
                // If we couldn't find a statement, fall back to the original element
                if (statementElement == null) {
                    statementElement = element;
                }

                // Get the statement's position in the document
                int elementStartOffset = statementElement.getTextRange().getStartOffset();
                int elementEndOffset = statementElement.getTextRange().getEndOffset();
                
                // Get line numbers
                int startLineNumber = document.getLineNumber(elementStartOffset);
                int endLineNumber = document.getLineNumber(elementEndOffset);
                
                // Check all lines that this statement spans
                for (int lineNumber = startLineNumber; lineNumber <= endLineNumber; lineNumber++) {
                    if (lineNumber >= 0 && lineNumber < document.getLineCount()) {
                        int lineStartOffset = document.getLineStartOffset(lineNumber);
                        int lineEndOffset = document.getLineEndOffset(lineNumber);
                        
                        // Get the current line text directly from document
                        String lineText = document.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString();
                        
                        // Check if line contains #notid (case insensitive and handle whitespace)
                        if (lineText.toLowerCase().matches(".*#\\s*notid.*")) {
                            return true;
                        }
                    }
                }
                
                return false;
            } catch (Exception e) {
                // If anything goes wrong, default to false (don't suppress the inspection)
                return false;
            }
        }

        private boolean containsChineseCharacters(String text) {
            // Unicode ranges for Chinese characters
            // Basic Chinese: U+4E00-U+9FFF
            // Extended ranges can be added as needed
            return text.matches(".*[\\u4E00-\\u9FFF].*");
        }

        private boolean isDocString(PyStringLiteralExpression node) {
            // Check if this string literal is a docstring
            PsiElement parent = node.getParent();
            
            // Docstrings are typically the first statement in a function, class, or module
            if (parent instanceof PyExpressionStatement) {
                PyExpressionStatement exprStmt = (PyExpressionStatement) parent;
                PsiElement grandParent = exprStmt.getParent();
                
                // Check if it's the first statement in a function body
                if (grandParent instanceof PyStatementList) {
                    PyStatementList stmtList = (PyStatementList) grandParent;
                    PsiElement greatGrandParent = stmtList.getParent();
                    
                    if (greatGrandParent instanceof PyFunction || 
                        greatGrandParent instanceof PyClass) {
                        // Check if this is the first statement
                        PyStatement[] statements = stmtList.getStatements();
                        if (statements.length > 0 && statements[0] == exprStmt) {
                            return true;
                        }
                    }
                }
                
                // Check if it's a module-level docstring
                if (grandParent instanceof PyFile) {
                    PyFile file = (PyFile) grandParent;
                    List<PyStatement> statements = file.getStatements();
                    if (!statements.isEmpty() && statements.getFirst() == exprStmt) {
                        return true;
                    }
                }
            }
            
            return false;
        }
    }
}
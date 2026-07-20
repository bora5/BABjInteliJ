package rs.co.bora5.plugins.babj.inspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;

/** Small shared PSI replacement/formatting helpers for BAB quick fixes. */
final class PsiReplacement {

    private PsiReplacement() {
    }

    static PsiElement replaceExpression(Project project, PsiElement expression, String text) {
        PsiElement replacement = JavaPsiFacade.getElementFactory(project)
                .createExpressionFromText(text, expression);
        return finish(project, expression.replace(replacement));
    }

    static PsiElement replaceStatement(Project project, PsiElement statement, String text) {
        PsiElement replacement = JavaPsiFacade.getElementFactory(project)
                .createStatementFromText(text, statement);
        return finish(project, statement.replace(replacement));
    }

    static PsiElement finish(Project project, PsiElement element) {
        PsiElement shortened = JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
        PsiElement formatted = CodeStyleManager.getInstance(project).reformat(shortened);
        if (formatted.getContainingFile() instanceof PsiJavaFile javaFile) {
            JavaCodeStyleManager.getInstance(project).optimizeImports(javaFile);
        }
        return formatted;
    }
}

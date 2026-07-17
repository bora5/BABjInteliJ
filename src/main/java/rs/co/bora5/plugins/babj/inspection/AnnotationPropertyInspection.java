package rs.co.bora5.plugins.babj.inspection;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;

import rs.co.bora5.plugins.babj.navigation.BABjArtifactResolver;

/** Validates entity-property strings used by common BABj and Vaadin binding annotations. */
public class AnnotationPropertyInspection extends AbstractBaseJavaLocalInspectionTool {

    private record Attribute(String annotationFqn, String attributeName) {
    }

    private static final List<Attribute> SUPPORTED = List.of(
            new Attribute("rs.co.bora5.programs.bab.front.views.interfaceCheck.AddCondition", "field"),
            new Attribute("rs.co.bora5.programs.bab.front.views.interfaceCheck.AdminVisibleFields", "fields"),
            new Attribute("rs.co.bora5.programs.bab.front.views.interfaceCheck.EnabledForStatus", "korisnik"),
            new Attribute("com.vaadin.flow.data.binder.PropertyId", "value"),
            new Attribute("rs.co.bora5.programs.bab.front.windowses.interfaceCheck.SingleUniqueField", "value")
    );

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                   boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                super.visitAnnotation(annotation);
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }
                PsiClass owner = PsiTreeUtil.getParentOfType(annotation, PsiClass.class, true);
                PsiClass entity = BABjArtifactResolver.entityFor(owner);
                if (entity == null) {
                    return;
                }

                for (Attribute supported : SUPPORTED) {
                    if (!supported.annotationFqn().equals(qualifiedName)) {
                        continue;
                    }
                    PsiAnnotationMemberValue value =
                            annotation.findDeclaredAttributeValue(supported.attributeName());
                    validateValue(value, entity, qualifiedName, holder);
                }
            }
        };
    }

    private static void validateValue(PsiAnnotationMemberValue value, PsiClass entity,
                                      String annotationName, ProblemsHolder holder) {
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                validateValue(initializer, entity, annotationName, holder);
            }
            return;
        }
        if (!(value instanceof PsiLiteralExpression literal)
                || !(literal.getValue() instanceof String path) || path.isBlank()) {
            return;
        }
        String normalized = path.startsWith("*") ? path.substring(1) : path;
        String error = BABjPsi.validatePath(entity, normalized.split("\\."), 0);
        if (error != null) {
            String shortName = annotationName.substring(annotationName.lastIndexOf('.') + 1);
            holder.registerProblem(literal,
                    "@" + shortName + ": '" + path + "' is not a valid property of "
                            + entity.getName() + " — " + error + ".");
        }
    }
}

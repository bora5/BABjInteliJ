package rs.co.bora5.plugins.babj.navigation;

import java.util.List;

import javax.swing.Icon;

import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;

/** Adds a BABj gutter marker to every recognized CRUD artifact. */
public class BABjLineMarkerProvider implements com.intellij.codeInsight.daemon.LineMarkerProvider {

    private static final Icon ICON = IconLoader.getIcon(
            "/icons/babjNavigate.svg", BABjLineMarkerProvider.class);

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiClass psiClass)
                || DumbService.isDumb(element.getProject())) {
            return null;
        }
        BABjArtifactRole role = BABjArtifactResolver.roleOf(psiClass);
        if (role == null) {
            return null;
        }
        List<PsiElement> targets = BABjArtifactResolver.relatedArtifacts(psiClass).stream()
                .map(BABjArtifactResolver.Artifact::psiClass)
                .map(PsiElement.class::cast)
                .toList();
        if (targets.size() < 2) {
            return null;
        }

        PsiClass entity = BABjArtifactResolver.entityFor(psiClass);
        String entityName = entity == null ? "module" : entity.getName();
        return NavigationGutterIconBuilder.create(ICON)
                .setTargets(targets)
                .setTooltipText("BABj " + role.getDisplayName()
                        + " — navigate through Entity -> DTO -> Home -> View -> Edit window")
                .setPopupTitle("BABj module: " + entityName)
                .createLineMarkerInfo(element);
    }
}

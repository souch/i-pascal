package com.siberika.idea.pascal.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siberika.idea.pascal.lang.psi.PasStruct;
import com.siberika.idea.pascal.lang.psi.PasTypeDecl;
import com.siberika.idea.pascal.lang.psi.PasTypeDeclaration;
import com.siberika.idea.pascal.lang.psi.PascalNamedElement;
import com.siberika.idea.pascal.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Author: George Bakhtadze
 * Date: 07/09/2013
 */
public class PasStructImpl extends PascalNamedElementImpl implements PasStruct {

    private List<Map<String, PasField>> members = null;
    private Set<PascalNamedElement> redeclaredMembers = null;

    private static final Map<String, PasField.Visibility> STR_TO_VIS;

    static {
        STR_TO_VIS = new HashMap<String, PasField.Visibility>(PasField.Visibility.values().length);
        STR_TO_VIS.put("STRICTPRIVATE", PasField.Visibility.STRICT_PRIVATE);
        STR_TO_VIS.put("PRIVATE", PasField.Visibility.PRIVATE);
        STR_TO_VIS.put("STRICTPROTECTED", PasField.Visibility.STRICT_PROTECTED);
        STR_TO_VIS.put("PROTECTED", PasField.Visibility.PROTECTED);
        STR_TO_VIS.put("PUBLIC", PasField.Visibility.PUBLIC);
        STR_TO_VIS.put("PUBLISHED", PasField.Visibility.PUBLISHED);
        STR_TO_VIS.put("AUTOMATED", PasField.Visibility.AUTOMATED);
        assert STR_TO_VIS.size() == PasField.Visibility.values().length;
    }

    public PasStructImpl(ASTNode node) {
        super(node);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static PasStructImpl findOwner(PascalRoutineImpl element) {
        return PsiTreeUtil.getParentOfType(element,
                PasClassHelperDeclImpl.class, PasClassTypeDeclImpl.class, PasInterfaceTypeDeclImpl.class, PasObjectDeclImpl.class, PasRecordHelperDeclImpl.class, PasRecordDeclImpl.class);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    protected PsiElement getNameElement() {
        PasTypeDeclaration typeDecl = PsiTreeUtil.getParentOfType(this, PasTypeDeclaration.class);
        return PsiUtil.findImmChildOfAnyType(typeDecl, PasGenericTypeIdentImpl.class);
    }

    /**
     * Returns structured type declaration element by its name element
     * @param namedElement name element
     * @return structured type declaration element
     */
    @Nullable
    public static PasStruct getStructByNameElement(final PascalNamedElement namedElement) {
        PsiElement sibling = PsiUtil.getNextSibling(namedElement);
        sibling = sibling != null ? PsiUtil.getNextSibling(sibling) : null;
        if ((sibling instanceof PasTypeDecl) && (sibling.getFirstChild() instanceof PasStruct)) {
            return (PasStruct) sibling.getFirstChild();
        }
        return null;
    }

    @Nullable
    @Override
    public PasField getField(String name) {
        if (members == null) {
            buildMembers();
        }
        for (PasField.Visibility visibility : PasField.Visibility.values()) {
            PasField result = members.get(visibility.ordinal()).get(name);
            if (null != result) {
                return result;
            }
        }
        return null;
    }

    private PasField.Visibility getVisibility(PsiElement element) {
        StringBuilder sb = new StringBuilder();
        PsiElement psiChild = element.getFirstChild();
        while (psiChild != null) {
            if (psiChild.getClass() == LeafPsiElement.class) {
                sb.append(psiChild.getText().toUpperCase());
            }
            psiChild = psiChild.getNextSibling();
        }
        return STR_TO_VIS.get(sb.toString());
    }

    synchronized private void buildMembers() {
        if (members != null) { return; }  // TODO: check correctness
        members = new ArrayList<Map<String, PasField>>(PasField.Visibility.values().length);
        for (PasField.Visibility visibility : PasField.Visibility.values()) {
            members.add(visibility.ordinal(), new LinkedHashMap<String, PasField>());
        }
        assert members.size() == PasField.Visibility.values().length;
        redeclaredMembers = new LinkedHashSet<PascalNamedElement>();
        System.out.println("buildMembers: " + getName());

        PasField.Visibility visibility = PasField.Visibility.PUBLISHED;
        PsiElement child = getFirstChild();
        while (child != null) {
            if (child.getClass() == PasRecordFieldImpl.class) {
                addFields(child, visibility);
            } else if (child.getClass() == PasClassFieldImpl.class) {
                addFields(child, visibility);
            } else if (child.getClass() == PasClassMethodImpl.class) {
                addField((PascalNamedElement) child, PasField.Type.ROUTINE, visibility);
            } else if (child.getClass() == PasClassPropertyImpl.class) {
                addField((PascalNamedElement) child, PasField.Type.PROPERTY, visibility);
            } else if (child.getClass() == PasVisibilityImpl.class) {
                visibility = getVisibility(child);
            } else if (child.getClass() == PasRecordFieldsImpl.class) {
                addVariantRecordFields(child, visibility);
            }
            child = child.getNextSibling();
        }
    }

    private void addVariantRecordFields(PsiElement element, PasField.Visibility visibility) {
        PsiElement child = element.getFirstChild();
        while (child != null) {
            if (child.getClass() == PasRecordFieldImpl.class) {
                addFields(child, visibility);
            }
            child = child.getNextSibling();
        }
    }

    private void addFields(PsiElement element, PasField.Visibility visibility) {
        PsiElement child = element.getFirstChild();
        while (child != null) {
            if (child.getClass() == PasNamedIdentImpl.class) {
                addField((PascalNamedElement) child, PasField.Type.VARIABLE, visibility);
            }
            child = child.getNextSibling();
        }
    }

    private void addField(PascalNamedElement element, PasField.Type type, PasField.Visibility visibility) {
        PasField field = new PasField(this, element, element.getName(), type, visibility);
        if (members.get(visibility.ordinal()) == null) {
            members.set(visibility.ordinal(), new LinkedHashMap<String, PasField>());
        }
        members.get(visibility.ordinal()).put(field.name, field);
    }

}

package com.strobel.assembler.metadata;

import com.strobel.core.StringComparator;
import com.strobel.core.VerifyArgument;

import java.util.List;

/**
 * @author Mike Strobel
 */
public abstract class MetadataResolver implements IMetadataResolver {
    @Override
    public TypeDefinition resolve(final TypeReference type) {
        final TypeReference t = VerifyArgument.notNull(type, "type").getUnderlyingType();

        if (t.isNested()) {
            final TypeDefinition declaringType = t.getDeclaringType().resolve();

            if (declaringType == null) {
                return null;
            }

            return getNestedType(declaringType.getDeclaredTypes(), type);
        }

        return resolveCore(t);
    }

    protected abstract TypeDefinition resolveCore(final TypeReference type);

        @Override
    public FieldDefinition resolve(final FieldReference field) {
        final TypeDefinition declaringType = resolve(VerifyArgument.notNull(field, "field").getDeclaringType());

        if (declaringType == null) {
            return null;
        }

        return getField(declaringType, field);
    }

    @Override
    public MethodDefinition resolve(final MethodReference method) {
        final TypeDefinition declaringType = resolve(VerifyArgument.notNull(method, "method").getDeclaringType());

        if (declaringType == null) {
            return null;
        }

        MethodReference reference = method;

        if (reference.isGenericMethod() && !reference.isGenericDefinition()) {
            reference = (MethodReference) ((IGenericInstance) reference).getGenericDefinition();
        }

        return getMethod(declaringType, reference);
    }

    // <editor-fold defaultstate="collapsed" desc="Member Resolution Helpers">

    final FieldDefinition getField(final TypeDefinition declaringType, final FieldReference reference) {
        TypeDefinition type = declaringType;

        while (type != null) {
            final FieldDefinition field = getField(declaringType.getDeclaredFields(), reference);

            if (field != null) {
                return field;
            }

            final TypeReference baseType = type.getBaseType();

            if (baseType == null) {
                return null;
            }

            type = resolve(baseType);
        }

        return null;
    }

    final MethodDefinition getMethod(final TypeDefinition declaringType, final MethodReference reference) {
        TypeDefinition type = declaringType;

        while (type != null) {
            final MethodDefinition method = getMethod(declaringType.getDeclaredMethods(), reference);

            if (method != null) {
                return method;
            }

            final TypeReference baseType = type.getBaseType();

            if (baseType == null) {
                return null;
            }

            type = resolve(baseType);
        }

        return null;
    }

    static TypeDefinition getNestedType (final List<TypeDefinition> candidates, final TypeReference reference) {
        for (int i = 0, n = candidates.size(); i < n; i++) {
            final TypeDefinition candidate = candidates.get(i);

            if (StringComparator.Ordinal.equals(candidate.getName(), reference.getName()))
                return candidate;
        }

        return null;
    }


    static FieldDefinition getField(final List<FieldDefinition> candidates, final FieldReference reference) {
        for (int i = 0, n = candidates.size(); i < n; i++) {
            final FieldDefinition candidate = candidates.get(i);

            if (!StringComparator.Ordinal.equals(candidate.getName(), reference.getName())) {
                continue;
            }

            if (!areEquivalent(candidate.getFieldType(), reference.getFieldType())) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    static MethodDefinition getMethod(final List<MethodDefinition> candidates, final MethodReference reference) {
        for (int i = 0, n = candidates.size(); i < n; i++) {
            final MethodDefinition candidate = candidates.get(i);

            if (!StringComparator.Ordinal.equals(candidate.getName(), reference.getName())) {
                continue;
            }

            if (candidate.hasGenericParameters() != reference.hasGenericParameters()) {
                continue;
            }

            if (candidate.hasGenericParameters() &&
                candidate.getGenericParameters().size() != reference.getGenericParameters().size()) {

                continue;
            }

            if (!areEquivalent(candidate.getReturnType(), reference.getReturnType())) {
                continue;
            }

            if (candidate.hasParameters() != reference.hasParameters()) {
                continue;
            }

            if (!candidate.hasParameters()) {
                return candidate;
            }

            if (!areParametersEquivalent(candidate.getParameters(), reference.getParameters())) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Equivalence Tests">

    static boolean areEquivalent(final TypeReference a, final TypeReference b) {
        if (a == b) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        if (a.getSimpleType() != b.getSimpleType()) {
            return false;
        }

        if (a.isGenericParameter()) {
            return b.isGenericParameter() &&
                   areEquivalent((GenericParameter) a, (GenericParameter) b);
        }

        if (a.isWildcardType()) {
            return b.isWildcardType() &&
                   areEquivalent(a.getExtendsBound(), b.getExtendsBound()) &&
                   areEquivalent(a.getSuperBound(), b.getSuperBound());
        }

        if (a.isGenericType()) {
            if (!b.isGenericType() || a.isGenericDefinition() != b.isGenericDefinition()) {
                return false;
            }

            if (a instanceof IGenericInstance) {
                return b instanceof IGenericInstance &&
                       areEquivalent((IGenericInstance) a, (IGenericInstance) b);
            }

            return areEquivalent(a.getUnderlyingType(), b.getUnderlyingType());
        }

        if (a.isArray()) {
            return areEquivalent(a.getUnderlyingType(), b.getUnderlyingType());
        }

        if (!StringComparator.Ordinal.equals(a.getName(), b.getName()) ||
            !StringComparator.Ordinal.equals(a.getPackageName(), b.getPackageName())) {

            return false;
        }

        // TODO: Check scope.

        return areEquivalent(a.getDeclaringType(), b.getDeclaringType());
    }

    static boolean areParametersEquivalent(final List<ParameterDefinition> a, final List<ParameterDefinition> b) {
        final int count = a.size();

        if (b.size() != count) {
            return false;
        }

        if (count == 0) {
            return true;
        }

        for (int i = 0; i < count; i++) {
            if (!areEquivalent(a.get(i).getParameterType(), b.get(i).getParameterType())) {
                return false;
            }
        }

        return true;
    }

    static boolean areEquivalent(final List<TypeReference> a, final List<TypeReference> b) {
        final int count = a.size();

        if (b.size() != count) {
            return false;
        }

        if (count == 0) {
            return true;
        }

        for (int i = 0; i < count; i++) {
            if (!areEquivalent(a.get(i), b.get(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean areEquivalent(final IGenericInstance a, final IGenericInstance b) {
        final List<TypeReference> typeArgumentsA = a.getTypeArguments();
        final List<TypeReference> typeArgumentsB = b.getTypeArguments();

        final int arity = typeArgumentsA.size();

        if (arity != typeArgumentsB.size()) {
            return false;
        }

        for (int i = 0; i < arity; i++) {
            if (!areEquivalent(typeArgumentsA.get(i), typeArgumentsB.get(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean areEquivalent(final GenericParameter a, final GenericParameter b) {
        return a.getPosition() == b.getPosition();
    }

    // </editor-fold>
}
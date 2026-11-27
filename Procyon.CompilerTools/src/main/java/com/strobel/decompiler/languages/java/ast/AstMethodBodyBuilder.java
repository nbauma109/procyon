/*
 * AstMethodBodyBuilder.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is based on Mono.Cecil from Jb Evain, Copyright (c) Jb Evain;
 * and ILSpy/ICSharpCode from SharpDevelop, Copyright (c) AlphaSierraPapa.
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.decompiler.languages.java.ast;

import com.strobel.annotations.NotNull;
import com.strobel.assembler.ir.ExceptionHandler;
import com.strobel.assembler.metadata.*;
import com.strobel.assembler.metadata.annotations.CustomAnnotation;
import com.strobel.core.ExceptionUtilities;
import com.strobel.core.Predicate;
import com.strobel.core.Predicates;
import com.strobel.core.StringUtilities;
import com.strobel.core.VerifyArgument;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.ast.*;
import com.strobel.decompiler.ast.Label;
import com.strobel.decompiler.languages.Languages;
import com.strobel.decompiler.patterns.AnyNode;
import com.strobel.decompiler.patterns.Choice;
import com.strobel.decompiler.patterns.INode;
import com.strobel.decompiler.patterns.Match;
import com.strobel.decompiler.patterns.OptionalNode;
import com.strobel.decompiler.semantics.ResolveResult;
import com.strobel.functions.Function;
import com.strobel.util.ContractUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.strobel.core.CollectionUtilities.*;

public class AstMethodBodyBuilder {
    private final AstBuilder _astBuilder;
    private final MethodDefinition _method;
    private final MetadataParser _parser;
    private final DecompilerContext _context;
    private final Set<Variable> _localVariablesToDefine = new LinkedHashSet<>();
    private final Set<Variable> _widenedVariables = new HashSet<>();
    private final Map<Variable, List<TypeReference>> _widenedLocals = new HashMap<>();

    private final static INode LAMBDA_BODY_PATTERN;
    private final static INode EMPTY_LAMBDA_BODY_PATTERN;

    static {
        LAMBDA_BODY_PATTERN = new Choice(
            new BlockStatement(
                new ExpressionStatement(new AnyNode("body").toExpression()),
                new OptionalNode(new ReturnStatement(Expression.MYSTERY_OFFSET)).toStatement()
            ),
            new BlockStatement(
                new ReturnStatement(Expression.MYSTERY_OFFSET, new AnyNode("body").toExpression())
            ),
            new AnyNode("body").toBlockStatement()
        );

        EMPTY_LAMBDA_BODY_PATTERN = new BlockStatement(new ReturnStatement(Expression.MYSTERY_OFFSET));
    }

    public static BlockStatement createMethodBody(
        final AstBuilder astBuilder,
        final MethodDefinition method,
        final DecompilerContext context,
        final Iterable<ParameterDeclaration> parameters) {

        VerifyArgument.notNull(astBuilder, "astBuilder");
        VerifyArgument.notNull(method, "method");
        VerifyArgument.notNull(context, "context");

        final MethodDefinition oldCurrentMethod = context.getCurrentMethod();

/*
        assert oldCurrentMethod == null ||
               oldCurrentMethod == method ||
               method.getDeclaringType().getDeclaringMethod() == oldCurrentMethod;
*/

        context.setCurrentMethod(method);

        try {
            final AstMethodBodyBuilder builder = new AstMethodBodyBuilder(astBuilder, method, context);
            return builder.createMethodBody(parameters);
        }
        catch (final Throwable t) {
            return createErrorBlock(astBuilder, context, method, t);
        }
        finally {
            context.setCurrentMethod(oldCurrentMethod);
        }
    }

    private static BlockStatement createErrorBlock(
        final AstBuilder astBuilder,
        final DecompilerContext context,
        final MethodDefinition method,
        final Throwable t) {

        final BlockStatement block = new BlockStatement();

        final List<String> lines = StringUtilities.split(
            ExceptionUtilities.getStackTraceString(t),
            true,
            '\r',
            '\n'
        );

        block.addChild(new Comment(" ", CommentType.SingleLine), Roles.COMMENT);
        block.addChild(new Comment(" This method could not be decompiled.", CommentType.SingleLine), Roles.COMMENT);
        block.addChild(new Comment(" ", CommentType.SingleLine), Roles.COMMENT);

        try {
            final PlainTextOutput bytecodeOutput = new PlainTextOutput();
            final DecompilationOptions bytecodeOptions = new DecompilationOptions();

            bytecodeOptions.getSettings().setIncludeLineNumbersInBytecode(false);

            Languages.bytecode().decompileMethod(method, bytecodeOutput, bytecodeOptions);

            final List<String> bytecodeLines = StringUtilities.split(
                bytecodeOutput.toString(),
                true,
                '\r',
                '\n'
            );

            block.addChild(new Comment(" Original Bytecode:", CommentType.SingleLine), Roles.COMMENT);
            block.addChild(new Comment(" ", CommentType.SingleLine), Roles.COMMENT);

            for (int i = 4; i < bytecodeLines.size(); i++) {
                final String line = StringUtilities.removeLeft(bytecodeLines.get(i), "      ");

                block.addChild(new Comment(line.replace("\t", "  "), CommentType.SingleLine), Roles.COMMENT);
            }

            block.addChild(new Comment(" ", CommentType.SingleLine), Roles.COMMENT);
        }
        catch (final Throwable ignored) {
            block.addChild(new Comment(" Could not show original bytecode, likely due to the same error.", CommentType.SingleLine), Roles.COMMENT);
            block.addChild(new Comment(" ", CommentType.SingleLine), Roles.COMMENT);
        }

        if (context.getSettings().getIncludeErrorDiagnostics()) {
            block.addChild(new Comment(" The error that occurred was:", CommentType.SingleLine), Roles.COMMENT);
            block.addChild(new Comment(" ", CommentType.SingleLine), Roles.COMMENT);

            for (final String line : lines) {
                block.addChild(new Comment(" " + line.replace("\t", "    "), CommentType.SingleLine), Roles.COMMENT);
            }

            block.addChild(new Comment(" ", CommentType.SingleLine), Roles.COMMENT);
        }

        try {
            final TypeDefinition currentType = astBuilder.getContext().getCurrentType();
            final IMetadataResolver resolver = currentType != null ? currentType.getResolver() : MetadataSystem.instance();
            final MetadataParser parser = new MetadataParser(resolver);

            block.add(
                new ThrowStatement(
                    new ObjectCreationExpression(
                           Expression.MYSTERY_OFFSET,
                        astBuilder.convertType(parser.parseTypeDescriptor("java/lang/IllegalStateException")),
                        new PrimitiveExpression( Expression.MYSTERY_OFFSET, "An error occurred while decompiling this method.")
                    )
                )
            );
        }
        catch (final Throwable ignored) {
            block.add(new EmptyStatement());
        }

        return block;
    }

    private AstMethodBodyBuilder(final AstBuilder astBuilder, final MethodDefinition method, final DecompilerContext context) {
        _astBuilder = astBuilder;
        _method = method;
        _context = context;
        _parser = new MetadataParser(method.getDeclaringType());
    }

    private BlockStatement createMethodBody(final Iterable<ParameterDeclaration> parameters) {
        final MethodBody body = _method.getBody();

        if (body == null) {
            return null;
        }

        final Block method = new Block();

        method.getBody().addAll(
            com.strobel.decompiler.ast.AstBuilder.build(body, true, _context)
        );

        AstOptimizer.optimize(_context, method);

        final MethodVariables mv = new MethodVariables(_method);

        populateVariables(mv, method);
        updateParameterDeclarations(mv.methodVariables, parameters);

        final BlockStatement astBlock = transformBlock(method);

        CommentStatement.replaceAll(astBlock);
        markEarlierLoads(astBlock);

        declareVariables(mv.methodVariables, astBlock);

        astBlock.acceptVisitor(
            new DepthFirstAstVisitor<MethodVariables, Void>() {
                @Override
                public Void visitLambdaExpression(final LambdaExpression node, final MethodVariables mv) {
                    super.visitLambdaExpression(node, mv);

                    final AstNode lambdaBody = node.getBody();
                    final DynamicCallSite callSite = node.getUserData(Keys.DYNAMIC_CALL_SITE);
                    final Lambda lambda = mv.lambdas.get(callSite);

                    if (lambda == null || !(lambdaBody instanceof BlockStatement)) {
                        return null;
                    }

                    final VariableInfo<Lambda> vi = mv.lambdaVariables.get(lambda);
                    final AstNodeCollection<ParameterDeclaration> parameterDeclarations = node.getParameters();

                    declareVariables(vi, (BlockStatement) lambdaBody);
                    updateParameterDeclarations(vi, parameterDeclarations);

                    final Match m = LAMBDA_BODY_PATTERN.match(lambdaBody);

                    if (m.success()) {
                        final AstNode bodyNode = first(m.<AstNode>get("body"));
                        bodyNode.remove();
                        node.setBody(bodyNode);

                        if (EMPTY_LAMBDA_BODY_PATTERN.matches(bodyNode)) {
                            bodyNode.getChildrenByRole(BlockStatement.STATEMENT_ROLE).clear();
                        }
                    }

                    return null;
                }
            },
            mv
        );

        return astBlock;
    }

    private void updateParameterDeclarations(final VariableInfo<?> vi, final Iterable<ParameterDeclaration> parameterDeclarations) {
        for (final Variable p : vi.methodParameters) {
            final ParameterDeclaration declaration = firstOrDefault(
                parameterDeclarations,
                new Predicate<ParameterDeclaration>() {
                    @Override
                    public boolean test(final ParameterDeclaration pd) {
                        return pd.getUserData(Keys.PARAMETER_DEFINITION) == p.getOriginalParameter();
                    }
                }
            );

            if (declaration != null) {
                declaration.setName(p.getName());
            }
        }
    }

    /** Applies the casts which a raw receiver requires to an invocation built before the widening. */
    private void keepEarlierMemberSelection(final InvocationExpression invocation) {
        final MemberReference member = invocation.getUserData(Keys.MEMBER_REFERENCE);

        if (member instanceof MethodReference && takesClassTypeVariables((MethodReference) member)) {
            final List<Expression> arguments = new ArrayList<>(invocation.getArguments());

            invocation.getArguments().clear();
            keepMemberSelection((MethodReference) member, arguments);
            invocation.getArguments().addAll(arguments);
        }
    }

    /**
     * A load may be built before the assignment which widens its local, as in the body of a loop: mark
     * it, along with the accesses to its members.
     */
    private void markEarlierLoads(final BlockStatement astBlock) {
        for (final AstNode node : astBlock.getDescendantsAndSelf()) {
            if (!(node instanceof IdentifierExpression) || node.getUserData(Keys.WIDENED_RECEIVER) != null) {
                continue;
            }

            final List<TypeReference> bounds = _widenedLocals.get(node.getUserData(Keys.VARIABLE));

            if (bounds == null) {
                continue;
            }

            Expression access = (Expression) node;

            access.putUserData(Keys.WIDENED_RECEIVER, true);
            access.putUserData(Keys.WIDENED_BOUNDS, bounds);

            while (true) {
                AstNode parent = access.getParent();

                if (parent instanceof MemberReferenceExpression &&
                    ((MemberReferenceExpression) parent).getTarget() == access) {

                    final AstNode invocation = parent.getParent();

                    if (invocation instanceof InvocationExpression &&
                        ((InvocationExpression) invocation).getTarget() == parent) {

                        parent = invocation;
                    }
                }
                else if (!(parent instanceof IndexerExpression) || ((IndexerExpression) parent).getTarget() != access) {
                    break;
                }

                markWidened((Expression) parent, access);

                if (parent instanceof InvocationExpression) {
                    keepEarlierMemberSelection((InvocationExpression) parent);
                }

                access = (Expression) parent;
            }
        }
    }

    private void declareVariables(final VariableInfo<?> vi, final BlockStatement astBlock) {
        final AstNodeCollection<Statement> statements = astBlock.getStatements();
        final Statement insertionPoint = firstOrDefault(statements);

        for (final Variable v : _localVariablesToDefine) {
            if (!vi.localVariables.contains(v)) {
                continue;
            }

            TypeReference variableType = v.getType();

            final TypeDefinition resolvedType = variableType.resolve();

            if (resolvedType != null && resolvedType.isAnonymous()) {
                if (resolvedType.getExplicitInterfaces().isEmpty()) {
                    variableType = resolvedType.getBaseType();
                }
                else {
                    variableType = resolvedType.getExplicitInterfaces().get(0);
                }
            }

            final AstType type = _astBuilder.convertType(variableType);
            final VariableDeclarationStatement declaration = new VariableDeclarationStatement(type, v.getName(), Expression.MYSTERY_OFFSET);

            declaration.putUserData(Keys.VARIABLE, v);
            statements.insertBefore(insertionPoint, declaration);
        }

        astBlock.putUserData(Keys.NAME_VARIABLES, vi.nv);
    }

    private BlockStatement transformBlock(final Block block) {
        final BlockStatement astBlock = new BlockStatement();

        if (block != null) {
            final List<Node> children = block.getChildren();
            for (int i = 0; i < children.size(); i++) {
                final Node node = children.get(i);

                final Statement statement = transformNode(
                    node,
                    i < children.size() - 1 ? children.get(i + 1) : null
                );

                astBlock.getStatements().add(statement);

                if (statement instanceof SynchronizedStatement) {
                    i++;
                }
            }
        }

        return astBlock;
    }

    private Statement transformNode(final Node node, final Node next) {
        if (node instanceof Label) {
            return new LabelStatement(Expression.MYSTERY_OFFSET,((Label) node).getName());
        }

        if (node instanceof Block) {
            return transformBlock((Block) node);
        }

        if (node instanceof com.strobel.decompiler.ast.Expression) {
            final com.strobel.decompiler.ast.Expression expression = (com.strobel.decompiler.ast.Expression) node;

            if (expression.getCode() == AstCode.MonitorEnter &&
                next instanceof TryCatchBlock) {

                final TryCatchBlock tryCatch = (TryCatchBlock) next;
                final Block finallyBlock = tryCatch.getFinallyBlock();

                if (finallyBlock != null &&
                    finallyBlock.getBody().size() == 1) {

                    final Node finallyNode = finallyBlock.getBody().get(0);

                    if (finallyNode instanceof com.strobel.decompiler.ast.Expression &&
                        ((com.strobel.decompiler.ast.Expression) finallyNode).getCode() == AstCode.MonitorExit) {

                        return transformSynchronized(expression, tryCatch);
                    }
                }
            }

            final List<Range> ranges = new ArrayList<>();

            final List<com.strobel.decompiler.ast.Expression> childExpressions = node.getSelfAndChildrenRecursive(
                com.strobel.decompiler.ast.Expression.class
            );

            for (final com.strobel.decompiler.ast.Expression e : childExpressions) {
                ranges.addAll(e.getRanges());
            }

            @SuppressWarnings("UnusedDeclaration")
            final List<Range> orderedAndJoinedRanges = Range.orderAndJoint(ranges);
            final AstNode codeExpression = transformExpression((com.strobel.decompiler.ast.Expression) node, true);

            if (codeExpression != null) {
                if (codeExpression instanceof Expression) {
                    return new ExpressionStatement(discardResultCast((Expression) codeExpression));
                }
                return (Statement) codeExpression;
            }
        }

        if (node instanceof Loop) {
            final Loop loop = (Loop) node;
            final Statement loopStatement;
            final com.strobel.decompiler.ast.Expression loopCondition = loop.getCondition();

            if (loopCondition != null) {
                if (loop.getLoopType() == LoopType.PostCondition) {
                    final DoWhileStatement doWhileStatement = new DoWhileStatement(loopCondition.getOffset());
                    doWhileStatement.setCondition((Expression) transformExpression(loopCondition, false));
                    loopStatement = doWhileStatement;
                }
                else {
                    final WhileStatement whileStatement = new WhileStatement(loopCondition.getOffset());
                    whileStatement.setCondition((Expression) transformExpression(loopCondition, false));
                    loopStatement = whileStatement;
                }
            }
            else {
                final WhileStatement whileStatement = new WhileStatement(Expression.MYSTERY_OFFSET);
                loopStatement = whileStatement;
                whileStatement.setCondition(new PrimitiveExpression(Expression.MYSTERY_OFFSET, true));
            }

            loopStatement.setChildByRole(Roles.EMBEDDED_STATEMENT, transformBlock(loop.getBody()));

            return loopStatement;
        }

        if (node instanceof Condition) {
            final Condition condition = (Condition) node;
            final com.strobel.decompiler.ast.Expression testCondition = condition.getCondition();
            final Block trueBlock = condition.getTrueBlock();
            final Block falseBlock = condition.getFalseBlock();
            final boolean hasFalseBlock = falseBlock.getEntryGoto() != null || !falseBlock.getBody().isEmpty();

            return new IfElseStatement(
                testCondition.getOffset(),
                (Expression) transformExpression(testCondition, false),
                transformBlock(trueBlock),
                hasFalseBlock ? transformBlock(falseBlock) : null
            );
        }

        if (node instanceof Switch) {
            final Switch switchNode = (Switch) node;
            final com.strobel.decompiler.ast.Expression testCondition = switchNode.getCondition();

            if (TypeAnalysis.isBoolean(testCondition.getInferredType())) {
                testCondition.setExpectedType(BuiltinTypes.Integer);
            }

            final List<CaseBlock> caseBlocks = switchNode.getCaseBlocks();
            final SwitchStatement switchStatement = new SwitchStatement((Expression) transformExpression(testCondition, false));

            for (final CaseBlock caseBlock : caseBlocks) {
                final SwitchSection section = new SwitchSection();
                final AstNodeCollection<CaseLabel> caseLabels = section.getCaseLabels();

                if (caseBlock.getValues().isEmpty()) {
                    caseLabels.add(new CaseLabel());
                }
                else {
                    final TypeReference referenceType;

                    if (testCondition.getExpectedType() != null) {
                        referenceType = testCondition.getExpectedType();
                    }
                    else {
                        referenceType = testCondition.getInferredType();
                    }

                    for (final Integer value : caseBlock.getValues()) {
                        final CaseLabel caseLabel = new CaseLabel();
                        caseLabel.setExpression(AstBuilder.makePrimitive(value, referenceType));
                        caseLabels.add(caseLabel);
                    }
                }

                section.getStatements().add(transformBlock(caseBlock));
                switchStatement.getSwitchSections().add(section);
            }

            return switchStatement;
        }

        if (node instanceof TryCatchBlock) {
            final TryCatchBlock tryCatchNode = ((TryCatchBlock) node);
            final Block finallyBlock = tryCatchNode.getFinallyBlock();
            final List<CatchBlock> catchBlocks = tryCatchNode.getCatchBlocks();

            final TryCatchStatement tryCatch = new TryCatchStatement( Expression.MYSTERY_OFFSET);

            tryCatch.setTryBlock(transformBlock(tryCatchNode.getTryBlock()));

            for (final CatchBlock catchBlock : catchBlocks) {
                final CatchClause catchClause = new CatchClause(transformBlock(catchBlock));
                final AstNodeCollection<AstType> exceptionTypes = catchClause.getExceptionTypes();

                for (final TypeReference caughtType : catchBlock.getCaughtTypes()) {
                    exceptionTypes.add(_astBuilder.convertType(caughtType));
                }

                final Variable exceptionVariable = catchBlock.getExceptionVariable();

                if (exceptionVariable != null) {
                    catchClause.setVariableName(exceptionVariable.getName());
                    catchClause.putUserData(Keys.VARIABLE, exceptionVariable);
                }

                tryCatch.getCatchClauses().add(catchClause);
            }

            if (finallyBlock != null && (!finallyBlock.getBody().isEmpty() || catchBlocks.isEmpty())) {
                tryCatch.setFinallyBlock(transformBlock(finallyBlock));
            }

            return tryCatch;
        }

        if (node instanceof BasicBlock) {
            final BasicBlock b = (BasicBlock) node;
            if (b.getChildren().size() == 1 && b.getChildren().get(0) instanceof Label) {
                final Label label = (Label) b.getChildren().get(0);
                return new LabelStatement(Expression.MYSTERY_OFFSET, label.getName());
            }
        }

        throw new IllegalArgumentException("Unknown node type: " + node);
    }

    private SynchronizedStatement transformSynchronized(final com.strobel.decompiler.ast.Expression expression, final TryCatchBlock tryCatch) {
        final SynchronizedStatement s = new SynchronizedStatement( expression.getOffset());

        s.setExpression((Expression) transformExpression(expression.getArguments().get(0), false));

        if (tryCatch.getCatchBlocks().isEmpty()) {
            s.setEmbeddedStatement(transformBlock(tryCatch.getTryBlock()));
        }
        else {
            tryCatch.setFinallyBlock(null);
            s.setEmbeddedStatement(new BlockStatement(transformNode(tryCatch, null)));
        }

        return s;
    }

    private AstNode transformExpression(final com.strobel.decompiler.ast.Expression e, final boolean isTopLevel) {
        return transformByteCode(e, isTopLevel);
    }

    @SuppressWarnings("ConstantConditions")
    private AstNode transformByteCode(final com.strobel.decompiler.ast.Expression byteCode, final boolean isTopLevel) {
        final Object operand = byteCode.getOperand();
        final Label label = operand instanceof Label ? (Label) operand : null;
        final AstType operandType = operand instanceof TypeReference ? _astBuilder.convertType((TypeReference) operand) : AstType.NULL;
        final Variable variableOperand = operand instanceof Variable ? (Variable) operand : null;
        final FieldReference fieldOperand = operand instanceof FieldReference ? (FieldReference) operand : null;

        final List<Expression> arguments = new ArrayList<>();

        for (final com.strobel.decompiler.ast.Expression e : byteCode.getArguments()) {
            arguments.add((Expression) transformExpression(e, false));
        }

        final Expression arg1 = arguments.size() >= 1 ? arguments.get(0) : null;
        final Expression arg2 = arguments.size() >= 2 ? arguments.get(1) : null;
        final Expression arg3 = arguments.size() >= 3 ? arguments.get(2) : null;

        switch (byteCode.getCode()) {
            case Nop:
                return null;

            case AConstNull:
                return new NullReferenceExpression( byteCode.getOffset());

            case LdC: {
                if (operand instanceof TypeReference) {
                    operandType.getChildrenByRole(Roles.TYPE_ARGUMENT).clear();
                    return new ClassOfExpression(byteCode.getOffset(), operandType);
                }

                if (operand instanceof MethodHandle) {
                    return new MethodHandlePlaceholder((MethodHandle) operand);
                }

                final TypeReference type = byteCode.getInferredType() != null ? byteCode.getInferredType()
                                                                              : byteCode.getExpectedType();

                if (type != null) {
                    switch (type.getSimpleType()) {
                        case Byte:
                        case Short:
                            return new PrimitiveExpression(
                                byteCode.getOffset(),
                                JavaPrimitiveCast.cast(JvmType.Integer, operand)
                            );

                        default:
                            return new PrimitiveExpression(
                                byteCode.getOffset(),
                                JavaPrimitiveCast.cast(type.getSimpleType(), operand)
                            );
                    }
                }

                return new PrimitiveExpression(byteCode.getOffset(), operand);
            }

            case Pop:
            case Pop2:
            case Dup:
            case DupX1:
            case DupX2:
            case Dup2:
            case Dup2X1:
            case Dup2X2:
                return arg1;

            case Swap:
                return arg1;

            case I2L:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Long), arg1);
            case I2F:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Float), arg1);
            case I2D:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Double), arg1);
            case L2I:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Integer), arg1);
            case L2F:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Float), arg1);
            case L2D:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Double), arg1);
            case F2I:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Integer), arg1);
            case F2L:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Long), arg1);
            case F2D:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Double), arg1);
            case D2I:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Integer), arg1);
            case D2L:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Long), arg1);
            case D2F:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Float), arg1);
            case I2B:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Byte), arg1);
            case I2C:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Character), arg1);
            case I2S:
                return new CastExpression(_astBuilder.convertType(BuiltinTypes.Short), arg1);

            case Goto:
                return new GotoStatement(byteCode.getOffset(), ((Label) operand).getName());

            case GetStatic: {
                final FieldReference field = sourceReference(fieldOperand, fieldOperand.resolve());
                final ConvertTypeOptions options = new ConvertTypeOptions();
                options.setIncludeTypeParameterDefinitions(false);
                final MemberReferenceExpression fieldReference = _astBuilder.convertType(field.getDeclaringType(), options)
                                                                            .member(field.getName());
                fieldReference.putUserData(Keys.MEMBER_REFERENCE, field);
                return fieldReference;
            }

            case PutStatic: {
                final ConvertTypeOptions options = new ConvertTypeOptions();
                options.setIncludeTypeParameterDefinitions(false);

                final FieldDefinition resolvedField = fieldOperand.resolve();
                final FieldReference field = sourceReference(fieldOperand, resolvedField);
                final Expression fieldReference;

                if (resolvedField != null &&
                    resolvedField.isFinal() &&
                    StringUtilities.equals(resolvedField.getDeclaringType().getInternalName(), _context.getCurrentType().getInternalName())) {

                    //
                    // Fields marked 'static final' cannot be initialized using a fully qualified name.
                    //

                    fieldReference = new IdentifierExpression( byteCode.getOffset(), fieldOperand.getName());
                }
                else {
                    fieldReference = _astBuilder.convertType(field.getDeclaringType(), options)
                                                .member(field.getName());
                }

                fieldReference.putUserData(Keys.MEMBER_REFERENCE, field);
                return new AssignmentExpression(fieldReference, eraseConsumedResult(arg1));
            }

            case GetField: {
                final MemberReferenceExpression fieldReference;
                final boolean widened = isWidened(arg1);

                if (arg1 instanceof ThisReferenceExpression &&
                    MetadataHelper.isSubType(_context.getCurrentType(), fieldOperand.getDeclaringType()) &&
                    !StringUtilities.equals(fieldOperand.getDeclaringType().getInternalName(), _context.getCurrentType().getInternalName())) {

                    fieldReference = new SuperReferenceExpression(arg1.getOffset()).member(fieldOperand.getName());
                }
                else {
                    fieldReference = discardResultCast(arg1).member(fieldOperand.getName());
                }

                fieldReference.putUserData(Keys.MEMBER_REFERENCE, fieldOperand);

                if (widened) {
                    markWidened(fieldReference, fieldReference.getTarget());
                }

                return fieldReference;
            }

            case PutField: {
                final MemberReferenceExpression fieldReference;
                final boolean widened = isWidened(arg1);

                if (arg1 instanceof ThisReferenceExpression &&
                    MetadataHelper.isSubType(_context.getCurrentType(), fieldOperand.getDeclaringType()) &&
                    !StringUtilities.equals(fieldOperand.getDeclaringType().getInternalName(), _context.getCurrentType().getInternalName())) {

                    fieldReference = new SuperReferenceExpression(arg1.getOffset()).member(fieldOperand.getName());
                }
                else if (widened && hasWildcardResult(arg1) && isTypedByClassVariables(fieldOperand)) {
                    // Nothing can be assigned to a field typed by the capture of a wildcard.
                    fieldReference = writableReceiver(arg1, fieldOperand, arg2).member(fieldOperand.getName());
                }
                else {
                    fieldReference = discardResultCast(arg1).member(fieldOperand.getName());
                }

                fieldReference.putUserData(Keys.MEMBER_REFERENCE, fieldOperand);

                final Expression value = eraseConsumedResult(arg2);

                return new AssignmentExpression(
                    fieldReference,
                    widened && isTypedByClassVariables(fieldOperand)
                    // Only a raw value still fits a field typed as before the widening.
                    ? eraseParameterizedResult(declaredType(fieldOperand), value)
                    : value
                );
            }

            case InvokeSpecial:
            case InvokeStatic:
                return transformCall(false, byteCode, arguments);
            case InvokeVirtual:
            case InvokeInterface:
                return transformCall(true, byteCode, arguments);

            case InvokeDynamic: {
                final DynamicCallSite callSite = (DynamicCallSite) operand;
                final MethodReference bootstrapMethod = callSite.getBootstrapMethod();

                if (CommonTypeReferences.LambdaMetafactory.isEquivalentTo(bootstrapMethod.getDeclaringType()) &&
                    ("metafactory".equals(bootstrapMethod.getName()) || "altMetafactory".equals(bootstrapMethod.getName())) &&
                    callSite.getBootstrapArguments().size() >= 3 &&
                    callSite.getBootstrapArguments().get(1) instanceof MethodHandle) {

                    final MethodHandle targetMethodHandle = (MethodHandle) callSite.getBootstrapArguments().get(1);
                    final MethodReference targetMethod = targetMethodHandle.getMethod();
                    final TypeReference declaringType = targetMethod.getDeclaringType();
                    final String methodName = targetMethod.isConstructor() ? "new" : targetMethod.getName();

                    final boolean hasInstanceArgument;

                    switch (targetMethodHandle.getHandleType()) {
                        case GetField:
                        case PutField:
                        case InvokeVirtual:
                        case InvokeInterface:
                        case InvokeSpecial:
//                            assert arg1 != null;
                            hasInstanceArgument = arg1 != null;
                            break;

                        default:
                            hasInstanceArgument = false;
                            break;
                    }

                    final MethodGroupExpression methodGroup = new MethodGroupExpression(
                        byteCode.getOffset(),
                        hasInstanceArgument ? arg1
                                            : new TypeReferenceExpression( byteCode.getOffset(), _astBuilder.convertType(declaringType)),
                        methodName
                    );

                    methodGroup.getClosureArguments().addAll(
                        hasInstanceArgument ? arguments.subList(1, arguments.size()) : arguments
                    );

                    methodGroup.putUserData(Keys.DYNAMIC_CALL_SITE, callSite);
                    methodGroup.putUserData(Keys.MEMBER_REFERENCE, targetMethod);

                    if (byteCode.getInferredType() != null) {
                        methodGroup.putUserData(Keys.TYPE_REFERENCE, byteCode.getInferredType());
                    }

                    // A raw receiver could select another overload of a member typed by its variables.
                    if (hasInstanceArgument &&
                        isWidened(arg1) &&
                        isOverloadedOnClassVariables(targetMethod) &&
                        callSite.getBootstrapArguments().get(2) instanceof IMethodSignature) {

                        final Map<String, TypeReference> typeArguments = receiverTypeArguments(
                            targetMethod,
                            (IMethodSignature) callSite.getBootstrapArguments().get(2),
                            byteCode.getInferredType()
                        );

                        typeArguments.putAll(thrownByDescriptor(targetMethod, byteCode.getInferredType()));

                        for (final String variable : typeArguments.keySet()) {
                            if (specializeReceiver(methodGroup, declaringType, typeArguments, variable)) {
                                return methodGroup;
                            }
                        }
                    }

                    // A widened receiver would throw the bound of its type variable.
                    if (hasInstanceArgument && isWidened(arg1)) {
                        final Map<String, TypeReference> thrown = thrownByDescriptor(targetMethod,
                                                                                    byteCode.getInferredType());

                        if (!thrown.isEmpty()) {
                            final Object signature = callSite.getBootstrapArguments().get(2);
                            final Map<String, TypeReference> typeArguments =
                                signature instanceof IMethodSignature
                                ? receiverTypeArguments(targetMethod,
                                                        (IMethodSignature) signature,
                                                        byteCode.getInferredType())
                                : new HashMap<String, TypeReference>();

                            typeArguments.putAll(thrown);

                            if (specializeReceiver(
                                    methodGroup,
                                    declaringType,
                                    typeArguments,
                                    thrown.keySet().iterator().next()
                                )) {

                                return methodGroup;
                            }
                        }
                    }

                    // The result of a member of a widened receiver only fits the raw functional interface.
                    TypeReference returned = declaredType(targetMethod);

                    while (returned != null && returned.isArray()) {
                        returned = returned.getElementType();
                    }

                    if (hasInstanceArgument &&
                        isWidened(arg1) &&
                        returned != null &&
                        returned.isGenericParameter()) {

                        final TypeReference functionType = byteCode.getInferredType();
                        final boolean generic = functionType instanceof IGenericInstance;
                        final Object implemented = callSite.getBootstrapArguments().get(2);

                        // Neither an inherited nor a fixed descriptor can be adapted, so the receiver is
                        // specialized.
                        if ((!generic ||
                             !targetMethod.getParameters().isEmpty() && withObjectResult(functionType) == null) &&
                            implemented instanceof IMethodSignature &&
                            specializeReceiver(
                                methodGroup,
                                declaringType,
                                receiverTypeArguments(targetMethod, (IMethodSignature) implemented, functionType),
                                returned.getSimpleName()
                            )) {

                            return methodGroup;
                        }

                        if (generic) {
                            return adaptReturnType(methodGroup, targetMethod, (IGenericInstance) functionType);
                        }
                    }

                    return methodGroup;
                }

                break;
            }

            case Bind: {
                final Lambda lambda = (Lambda) byteCode.getOperand();
                final LambdaExpression lambdaExpression = new LambdaExpression(byteCode.getOffset());
                final AstNodeCollection<ParameterDeclaration> declarations = lambdaExpression.getParameters();

                for (final Variable v : lambda.getParameters()) {
                    final ParameterDefinition p = v.getOriginalParameter();
                    final ParameterDeclaration d = new ParameterDeclaration(v.getName(), null);

                    d.putUserData(Keys.PARAMETER_DEFINITION, p);
                    d.putUserData(Keys.VARIABLE, v);

                    for (final CustomAnnotation annotation : p.getAnnotations()) {
                        d.getAnnotations().add(_astBuilder.createAnnotation(annotation));
                    }

                    declarations.add(d);

                    if (p.isFinal()) {
                        EntityDeclaration.addModifier(d, Flags.Flag.FINAL);
                    }
                }

                final BlockStatement body = transformBlock(lambda.getBody());

                lambdaExpression.setBody(body);
                lambdaExpression.putUserData(Keys.TYPE_REFERENCE, byteCode.getInferredType());

                final DynamicCallSite callSite = lambda.getCallSite();

                if (callSite != null) {
                    lambdaExpression.putUserData(Keys.DYNAMIC_CALL_SITE, callSite);
                }

                return lambdaExpression;
            }

            case ArrayLength:
                final MemberReferenceExpression length = discardResultCast(arg1).member("length");
                final Variable array = arg1.getUserData(Keys.VARIABLE);
                final TypeReference arrayType = _widenedVariables.contains(array)
                                                ? array.getType()
                                                : single(byteCode.getArguments()).getInferredType();

                if (arrayType != null) {
                    length.putUserData(
                        Keys.MEMBER_REFERENCE,
                        _parser.parseField(arrayType, "length", "I")
                    );
                }

                return length;

            case AThrow:
                return new ThrowStatement(arg1);

            case CheckCast:
                return new CastExpression(operandType, arg1);

            case InstanceOf:
                return new InstanceOfExpression( byteCode.getOffset(), arg1, operandType);

            case MonitorEnter:
            case MonitorExit:
                break;

            case MultiANewArray: {
                final ArrayCreationExpression arrayCreation =
                        new ArrayCreationExpression( byteCode.getOffset());

                int rank = 0;
                AstType elementType = operandType;

                while (elementType instanceof ComposedType) {
                    rank += ((ComposedType) elementType).getArraySpecifiers().size();
                    elementType = ((ComposedType) elementType).getBaseType();
                }

                arrayCreation.setType(elementType.clone());

                for (int i = 0; i < arguments.size(); i++) {
                    arrayCreation.getDimensions().add(arguments.get(i));
                    --rank;
                }

                for (int i = 0; i < rank; i++) {
                    arrayCreation.getAdditionalArraySpecifiers().add(new ArraySpecifier());
                }

                return arrayCreation;
            }

            case Breakpoint:
                return null;

            case Load: {
                if (!variableOperand.isParameter()) {
                    _localVariablesToDefine.add(variableOperand);
                }
                if (variableOperand.isParameter() && variableOperand.getOriginalParameter().getPosition() < 0) {
                    final ThisReferenceExpression self = new ThisReferenceExpression( byteCode.getOffset());
                    self.putUserData(Keys.TYPE_REFERENCE, _context.getCurrentType());
                    return self;
                }
                final IdentifierExpression name = new IdentifierExpression( byteCode.getOffset(),
                        variableOperand.getName());
                name.putUserData(Keys.VARIABLE, variableOperand);

                final List<TypeReference> bounds = _widenedLocals.get(variableOperand);

                if (bounds != null) {
                    name.putUserData(Keys.WIDENED_RECEIVER, true);
                    name.putUserData(Keys.WIDENED_BOUNDS, bounds);
                }

                return name;
            }

            case Store: {
                if (!variableOperand.isParameter()) {
                    _localVariablesToDefine.add(variableOperand);
                }
                final IdentifierExpression name = new IdentifierExpression( byteCode.getOffset(),
                        variableOperand.getName());
                name.putUserData(Keys.VARIABLE, variableOperand);
                widenVariable(variableOperand, arg1);
                return new AssignmentExpression(name, eraseConsumedResult(arg1));
            }

            case LoadElement: {
                // The compiler may cast an array of the widened type before accessing its elements.
                Expression indexed = arg1;

                while (indexed instanceof CastExpression && !isWidened(indexed)) {
                    indexed = ((CastExpression) indexed).getExpression();
                }

                final TypeReference indexedType = indexed.getUserData(Keys.WIDENED_TYPE);
                final boolean widened = isWidened(indexed) || indexedType != null && indexedType.isArray();
                final Expression source = indexed == arg1 ? null : indexed;
                final IndexerExpression element = new IndexerExpression( byteCode.getOffset(), discardResultCast(arg1), arg2);

                if (widened) {
                    markWidened(element, source != null ? source : element.getTarget());
                }

                final TypeReference widenedArray = element.getTarget().getUserData(Keys.WIDENED_TYPE);

                if (widenedArray != null && widenedArray.isArray()) {
                    element.putUserData(Keys.WIDENED_TYPE, widenedArray.getElementType());
                }

                return element;
            }
            case StoreElement: {
                final boolean widened = isWidened(arg1);
                final Expression target = discardResultCast(arg1);
                final TypeReference storedArray = widened ? declaredTypeOf(target) : null;
                final Expression value = eraseConsumedResult(arg3);

                return new AssignmentExpression(
                    new IndexerExpression( byteCode.getOffset(), target, arg2),
                    storedArray != null && storedArray.isArray()
                    // Only a raw value still fits an element typed as before the widening.
                    ? eraseParameterizedResult(storedArray.getElementType(), value)
                    : value
                );
            }

            case Add:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.ADD, arg2);
            case Sub:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.SUBTRACT, arg2);
            case Mul:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.MULTIPLY, arg2);
            case Div:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.DIVIDE, arg2);
            case Rem:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.MODULUS, arg2);
            case Neg:
                return new UnaryOperatorExpression(UnaryOperatorType.MINUS, arg1);
            case Shl:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.SHIFT_LEFT, arg2);
            case Shr:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.SHIFT_RIGHT, arg2);
            case UShr:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.UNSIGNED_SHIFT_RIGHT, arg2);
            case And:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.BITWISE_AND, arg2);
            case Or:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.BITWISE_OR, arg2);
            case Not:
                return new UnaryOperatorExpression(UnaryOperatorType.NOT, arg1);
            case Xor:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.EXCLUSIVE_OR, arg2);

            case Inc: {
                if (!variableOperand.isParameter()) {
                    _localVariablesToDefine.add(variableOperand);
                }

                final IdentifierExpression name = new IdentifierExpression( byteCode.getOffset(),
                        variableOperand.getName());

                name.getIdentifierToken().putUserData(Keys.VARIABLE, variableOperand);
                name.putUserData(Keys.VARIABLE, variableOperand);

                final PrimitiveExpression deltaExpression = (PrimitiveExpression) arg1;
                final int delta = (int) JavaPrimitiveCast.cast(JvmType.Integer, deltaExpression.getValue());

                switch (delta) {
                    case -1:
                        return new UnaryOperatorExpression(UnaryOperatorType.DECREMENT, name);
                    case 1:
                        return new UnaryOperatorExpression(UnaryOperatorType.INCREMENT, name);
                    default:
                        return new AssignmentExpression(name, AssignmentOperatorType.ADD, arg1);
                }
            }

            case CmpEq:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.EQUALITY, arg2);
            case CmpNe:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.INEQUALITY, arg2);
            case CmpLt:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.LESS_THAN, arg2);
            case CmpGe:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.GREATER_THAN_OR_EQUAL, arg2);
            case CmpGt:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.GREATER_THAN, arg2);
            case CmpLe:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.LESS_THAN_OR_EQUAL, arg2);

            case Return:
                return new ReturnStatement(byteCode.getOffset(), arg1 != null ? eraseConsumedResult(arg1) : null);

            case NewArray: {
                final ArrayCreationExpression arrayCreation =
                        new ArrayCreationExpression( byteCode.getOffset());

                TypeReference elementType = operandType.getUserData(Keys.TYPE_REFERENCE);

                while (elementType.isArray()) {
                    arrayCreation.getAdditionalArraySpecifiers().add(new ArraySpecifier());
                    elementType = elementType.getElementType();
                }

                arrayCreation.setType(_astBuilder.convertType(elementType));
                arrayCreation.getDimensions().add(arg1);

                return arrayCreation;
            }

            case LogicalNot:
                return new UnaryOperatorExpression(UnaryOperatorType.NOT, arg1);

            case LogicalAnd:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.LOGICAL_AND, arg2);
            case LogicalOr:
                return new BinaryOperatorExpression(arg1, BinaryOperatorType.LOGICAL_OR, arg2);

            case InitObject:
                return transformCall(false, byteCode, arguments);

            case InitArray: {
                final ArrayCreationExpression arrayCreation =
                        new ArrayCreationExpression( byteCode.getOffset());

                TypeReference elementType = operandType.getUserData(Keys.TYPE_REFERENCE);

                while (elementType.isArray()) {
                    arrayCreation.getAdditionalArraySpecifiers().add(new ArraySpecifier());
                    elementType = elementType.getElementType();
                }

                arrayCreation.setType(_astBuilder.convertType(elementType));
                arrayCreation.setInitializer(new ArrayInitializerExpression(arguments));

                return arrayCreation;
            }

            case Wrap:
                return null;

            case TernaryOp:
                return new ConditionalExpression(arg1, arg2, arg3);

            case LoopOrSwitchBreak:
                return label != null ? new GotoStatement(byteCode.getOffset(), label.getName()) : new BreakStatement(byteCode.getOffset());

            case LoopContinue:
                return label != null ? new ContinueStatement(byteCode.getOffset(), label.getName()) : new ContinueStatement( byteCode.getOffset());

            case CompoundAssignment:
                throw ContractUtils.unreachable();

            case PreIncrement: {
                final Integer incrementAmount = (Integer) operand;
                if (incrementAmount < 0) {
                    return new UnaryOperatorExpression(UnaryOperatorType.DECREMENT, arg1);
                }
                return new UnaryOperatorExpression(UnaryOperatorType.INCREMENT, arg1);
            }

            case PostIncrement: {
                final Integer incrementAmount = (Integer) operand;
                if (incrementAmount < 0) {
                    return new UnaryOperatorExpression(UnaryOperatorType.POST_DECREMENT, arg1);
                }
                return new UnaryOperatorExpression(UnaryOperatorType.POST_INCREMENT, arg1);
            }

            case Box:
            case Unbox:
                throw ContractUtils.unreachable();

            case Leave:
            case EndFinally:
                return null;

            case DefaultValue:
                return AstBuilder.makeDefaultValue((TypeReference) operand);
        }

        final Expression inlinedAssembly = inlineAssembly(byteCode, arguments);

        if (operand instanceof DynamicCallSite) {
            inlinedAssembly.putUserData(Keys.DYNAMIC_CALL_SITE, (DynamicCallSite) operand);
        }

        if (isTopLevel) {
            return new ExpressionStatement(inlinedAssembly);
        }

        return inlinedAssembly;
    }

    /**
     * Returns the reference to use in the source code for a static member.  The compiler names the class
     * from which the member is accessed, which has no name of its own if it is anonymous: the type
     * declaring the inherited member must be used instead, as in the body of an enum constant accessing
     * another constant.
     */
    private static <T extends MemberReference> T sourceReference(final T member, final IMemberDefinition definition) {
        final TypeDefinition qualifier = member.getDeclaringType().resolve();

        if (qualifier != null && qualifier.isAnonymous() && definition != null) {
            @SuppressWarnings("unchecked")
            final T declared = (T) definition;
            return declared;
        }

        return member;
    }

    private Expression transformCall(
        final boolean isVirtual,
        final com.strobel.decompiler.ast.Expression byteCode,
        final List<Expression> arguments) {

        MethodReference methodReference = (MethodReference) byteCode.getOperand();

        if (byteCode.getCode() == AstCode.InvokeStatic) {
            methodReference = sourceReference(methodReference, methodReference.resolve());
        }

        final boolean hasThis = byteCode.getCode() == AstCode.InvokeVirtual ||
                                byteCode.getCode() == AstCode.InvokeInterface ||
                                byteCode.getCode() == AstCode.InvokeSpecial;

        Expression target;
        boolean widenedReceiver = false;

        final TypeReference declaringType = methodReference.getDeclaringType();

        if (hasThis) {
            final Expression receiver = arguments.remove(0);

            widenedReceiver = isWidened(receiver);

            // A widened receiver would throw the bound of its type variable.
            final Map<String, TypeReference> thrown =
                widenedReceiver ? thrownTypeArguments(methodReference, byteCode.getOffset())
                                : Collections.<String, TypeReference>emptyMap();
            final boolean takesVariables = widenedReceiver && takesClassTypeVariables(methodReference);

            if (takesVariables && thrown.isEmpty()) {
                // Only a raw receiver still takes the arguments typed as before the widening.
                target = rawReceiver(receiver, declaringType);
            }
            else {
                final Expression discarded = discardResultCast(receiver);

                target = discarded;

                if (!thrown.isEmpty()) {
                    final Map<String, TypeReference> typeArguments = argumentTypeArguments(methodReference, arguments);

                    typeArguments.putAll(thrown);
                    target = specializedReceiver(
                        discarded,
                        declaringType,
                        typeArguments,
                        thrown.keySet().iterator().next()
                    );
                }

                if (takesVariables && target == discarded) {
                    target = rawReceiver(discarded, declaringType);
                }
            }

            if (target instanceof NullReferenceExpression) {
                target = new CastExpression(_astBuilder.convertType(declaringType), target);
            }
        }
        else {
            final MethodDefinition resolvedMethod;

            if (byteCode.getCode() == AstCode.InvokeStatic &&
                declaringType.isEquivalentTo(_context.getCurrentType()) &&
                (!_context.getSettings().getForceExplicitTypeArguments() ||
                 (resolvedMethod = methodReference.resolve()) == null ||
                 !resolvedMethod.isGenericMethod())) {

                target = Expression.NULL;
            }
            else {
                final ConvertTypeOptions options = new ConvertTypeOptions();
                options.setIncludeTypeArguments(false);
                options.setIncludeTypeParameterDefinitions(false);
                options.setAllowWildcards(false);
                target = new TypeReferenceExpression( byteCode.getOffset(), _astBuilder.convertType(declaringType, options));
            }
        }

        if (target instanceof ThisReferenceExpression) {
            if (!isVirtual && !declaringType.isEquivalentTo(_method.getDeclaringType())) {
                target = new SuperReferenceExpression( byteCode.getOffset());
                target.putUserData(Keys.TYPE_REFERENCE, declaringType);
            }
        }
        else if (methodReference.isConstructor()) {
            final ObjectCreationExpression creation;
            final TypeDefinition resolvedType = declaringType.resolve();

            if (resolvedType != null) {
                final AstType declaredType;

                TypeReference instantiatedType;

                if (resolvedType.isAnonymous()) {
                    if (resolvedType.getExplicitInterfaces().isEmpty()) {
                        instantiatedType = resolvedType.getBaseType();
                    }
                    else {
                        instantiatedType = resolvedType.getExplicitInterfaces().get(0);
                    }
                }
                else {
                    instantiatedType = resolvedType;
                }

                final List<TypeReference> typeArguments = byteCode.getUserData(AstKeys.TYPE_ARGUMENTS);

                if (typeArguments != null &&
                    resolvedType.isGenericDefinition() &&
                    typeArguments.size() == resolvedType.getGenericParameters().size()) {

                    instantiatedType = instantiatedType.makeGenericType(typeArguments);
                }

                declaredType = _astBuilder.convertType(instantiatedType);

                if (resolvedType.isAnonymous()) {
                    creation = new AnonymousObjectCreationExpression(
                        byteCode.getOffset(),
                        _astBuilder.createType(resolvedType).clone(),
                        declaredType
                    );
                }
                else {
                    creation = new ObjectCreationExpression( byteCode.getOffset(), declaredType);
                }
            }
            else {
                final ConvertTypeOptions options = new ConvertTypeOptions();
                options.setIncludeTypeParameterDefinitions(false);
                creation = new ObjectCreationExpression( byteCode.getOffset(), _astBuilder.convertType(declaringType, options));
            }

            creation.getArguments().addAll(adjustArgumentsForMethodCall(methodReference, null, arguments));
            creation.putUserData(Keys.MEMBER_REFERENCE, methodReference);

            return creation;
        }

        final InvocationExpression invocation;

        if (methodReference.isConstructor()) {
            invocation = new InvocationExpression(
                byteCode.getOffset(),
                target,
                adjustArgumentsForMethodCall(methodReference, null, arguments)
            );
        }
        else {
            final List<Expression> adjustedArguments = adjustArgumentsForMethodCall(
                methodReference,
                hasThis ? target : null,
                arguments
            );

            if (target.getUserData(Keys.RESULT_CAST) != null && takesClassTypeVariables(methodReference)) {
                keepMemberSelection(methodReference, adjustedArguments);
            }

            // The type arguments inferred from the arguments do not hold for a widened argument.
            final boolean widened = any(adjustedArguments, a -> a.getUserData(Keys.SELECTS_OVERLOAD) != null);

            invocation = target.invoke(
                methodReference.getName(),
                widened ? widenedTypeArguments(methodReference, adjustedArguments)
                        : convertTypeArguments(methodReference),
                adjustedArguments
            );

            if (target.isNull()) {
                invocation.setOffset(byteCode.getOffset());
            }

            if (widened) {
                invocation.putUserData(Keys.MEMBER_REFERENCE, methodReference);
                invocation.putUserData(Keys.WIDENED_BOUNDS, widenedBounds(methodReference.resolve(), adjustedArguments));

                final TypeReference resultType = widenedResultType(methodReference.resolve(), adjustedArguments);

                if (resultType != null) {
                    invocation.putUserData(Keys.WIDENED_TYPE, resultType);

                    if (!resultType.isArray()) {
                        invocation.putUserData(Keys.WIDENED_RECEIVER, true);
                    }
                }

                final MethodDefinition definition = methodReference.resolve();
                final TypeReference dependentBound = dependentResultBound(definition, adjustedArguments);

                return eraseParameterizedResult(
                    dependentBound != null ? dependentBound : definition.getReturnType(),
                    invocation
                );
            }
        }

        invocation.putUserData(Keys.MEMBER_REFERENCE, methodReference);

        if (widenedReceiver) {
            markWidened(invocation, target);
        }

        return invocation;
    }

    /**
     * Widening an argument also changes the type arguments of a parameterized result which depends on
     * it, and no cast of that result survives erasure: the raw type is compatible with what was
     * expected.
     */
    private Expression eraseParameterizedResult(final TypeReference declared, final Expression result) {
        TypeReference element = declared;

        while (element.isArray()) {
            element = element.getElementType();
        }

        if (element instanceof IGenericInstance &&
            element.containsGenericParameters() &&
            !(result instanceof NullReferenceExpression)) {
            TypeReference target = MetadataHelper.erase(declared);

            if (!isAccessibleType(element)) {
                // Such a result can only be consumed as one of its supertypes.
                target = accessibleGenericSupertype(element);

                if (target == null) {
                    return result;
                }

                for (TypeReference type = declared; type.isArray(); type = type.getElementType()) {
                    target = target.makeArrayType();
                }
            }

            final CastExpression cast = new CastExpression(_astBuilder.convertType(target), result);
            cast.putUserData(Keys.RESULT_CAST, true);
            return cast;
        }

        return result;
    }

    /**
     * Returns the type of a result declared as a type variable of a widened parameter, or as an array of
     * it, or {@code null}.
     */
    private static TypeReference widenedResultType(final MethodDefinition method, final List<Expression> arguments) {
        TypeReference declared = method.getReturnType();
        int rank = 0;

        while (declared.isArray()) {
            declared = declared.getElementType();
            rank++;
        }

        if (!declared.isGenericParameter()) {
            return null;
        }

        for (int i = 0; i < arguments.size(); i++) {
            TypeReference type = arguments.get(i).getUserData(Keys.SELECTS_OVERLOAD);

            if (type != null &&
                variableChain(method.getParameters().get(i).getParameterType()).contains(declared.getSimpleName())) {

                // Of an intersection, the first type is the one to which the compiler erases it.
                if (type instanceof ICompoundType) {
                    final ICompoundType intersection = (ICompoundType) type;

                    type = intersection.getBaseType() != null ? intersection.getBaseType()
                                                              : first(intersection.getInterfaces());
                }

                while (rank-- > 0) {
                    type = type.makeArrayType();
                }

                return type;
            }
        }

        return null;
    }

    /** Returns the names of a type variable and of the type variables which bound it. */
    private static List<String> variableChain(final TypeReference variable) {
        final List<String> names = new ArrayList<>();

        TypeReference type = variable;

        while (type.isGenericParameter()) {
            names.add(type.getSimpleName());

            if (!((GenericParameter) type).hasExtendsBound()) {
                break;
            }

            type = ((GenericParameter) type).getExtendsBound();
        }

        return names;
    }

    /** Returns the widened type of a value, which may come from either branch of nested conditionals. */
    private static TypeReference widenedTypeOf(final Expression value) {
        if (!(value instanceof ConditionalExpression)) {
            return value.getUserData(Keys.WIDENED_TYPE);
        }

        final TypeReference first = widenedTypeOf(((ConditionalExpression) value).getTrueExpression());
        final TypeReference second = widenedTypeOf(((ConditionalExpression) value).getFalseExpression());

        if (first == null || second != null && MetadataHelper.isAssignableFrom(second, first)) {
            return second;
        }

        return second == null || MetadataHelper.isAssignableFrom(first, second)
               ? first
               : MetadataHelper.findCommonSuperType(first, second);
    }

    /**
     * Without debug information, the type of a local was inferred from the argument before its widening.
     */
    private void widenVariable(final Variable variable, final Expression value) {
        if (value instanceof ConditionalExpression) {
            final Expression whenTrue = ((ConditionalExpression) value).getTrueExpression();
            final Expression whenFalse = ((ConditionalExpression) value).getFalseExpression();
            final TypeReference first = widenedTypeOf(whenTrue);
            final TypeReference second = widenedTypeOf(whenFalse);

            // Either branch may hold the widened value.
            widenVariable(variable, whenTrue);
            widenVariable(variable, whenFalse);

            final TypeReference current = variable.getType();

            if (first != null && second != null && current != null &&
                _widenedVariables.contains(variable) &&
                (!MetadataHelper.isAssignableFrom(current, first) ||
                 !MetadataHelper.isAssignableFrom(current, second))) {

                variable.setType(MetadataHelper.findCommonSuperType(first, second));
            }

            return;
        }

        final TypeReference type = value.getUserData(Keys.WIDENED_TYPE);
        final VariableDefinition original = variable.getOriginalVariable();

        if (variable.isParameter() || original != null && original.isFromMetadata()) {
            return;
        }

        Expression source = value;

        while (source instanceof CastExpression && !isWidened(source)) {
            source = ((CastExpression) source).getExpression();
        }

        final List<TypeReference> bounds = source.getUserData(Keys.WIDENED_BOUNDS);

        if (bounds != null) {
            // The members of the local are only known as far as the widening allows.
            _widenedLocals.put(variable, bounds);
        }

        if (type != null &&
            variable.getType() != null &&
            MetadataHelper.isAssignableFrom(type, variable.getType())) {

            variable.setType(type);
            _widenedVariables.add(variable);
        }
        else if (type != null &&
                 _widenedVariables.contains(variable) &&
                 !MetadataHelper.isAssignableFrom(variable.getType(), type)) {

            // Another assignment widened the local to an unrelated type.
            variable.setType(MetadataHelper.findCommonSuperType(variable.getType(), type));
        }
    }

    /**
     * An argument whose type depends on a widened type variable, such as a {@code List<T>} beside a
     * {@code T}, is only still accepted as a raw type.
     */
    private void reconcileDependentArguments(
        final MethodReference method,
        final List<ParameterDefinition> parameters,
        final List<Expression> originals,
        final Map<Integer, TypeReference> argumentTypes,
        final List<Expression> arguments) {

        final Set<String> widened = new HashSet<>();
        final Map<TypeReference, TypeReference> substitutions = new HashMap<>();
        final MethodDefinition definition = method.resolve();

        for (int i = 0; i < arguments.size(); i++) {
            final TypeReference bound = arguments.get(i).getUserData(Keys.SELECTS_OVERLOAD);

            if (bound != null) {
                final TypeReference variable = definition.getParameters().get(parameters.get(i).getPosition()).getParameterType();

                widened.addAll(variableChain(variable));
                substitutions.put(variable, bound);
            }
        }

        if (widened.isEmpty()) {
            return;
        }

        for (int i = 0; i < arguments.size(); i++) {
            final TypeReference declared = definition.getParameters().get(parameters.get(i).getPosition()).getParameterType();
            final Expression original = originals.get(i);

            TypeReference element = declared;

            while (element.isArray()) {
                element = element.getElementType();
            }

            final TypeReference argumentType = argumentTypes.get(parameters.get(i).getPosition());

            if (element instanceof IGenericInstance &&
                !(original instanceof NullReferenceExpression) &&
                any(widened, name -> mentions(declared, name)) &&
                !remainsCompatible(declared, substitutions, original, argumentType)) {

                final TypeReference rawType = MetadataHelper.erase(declared);

                original.remove();

                final CastExpression cast = new CastExpression(
                    _astBuilder.convertType(rawType),
                    keepSpecialization(original)
                );

                cast.putUserData(Keys.SELECTS_OVERLOAD, rawType);
                arguments.set(i, cast);
            }
        }
    }

    /** Tells whether an argument can still be passed once the widened variables are substituted. */
    private static boolean remainsCompatible(
        final TypeReference declared,
        final Map<TypeReference, TypeReference> substitutions,
        final Expression argument,
        final TypeReference argumentType) {

        if (argumentType == null ||
            argument instanceof LambdaExpression ||
            argument instanceof MethodGroupExpression) {

            return false;
        }

        final TypeReference substituted = TypeSubstitutionVisitor.instance().visit(declared, substitutions);

        return substituted != declared && accepts(substituted, argumentType);
    }

    /**
     * A lambda or method reference takes its parameter types from its functional interface, which a
     * raw cast alone would erase.
     */
    private Expression keepSpecialization(final Expression argument) {
        final TypeReference type = argument instanceof LambdaExpression || argument instanceof MethodGroupExpression
                                   ? argument.getUserData(Keys.TYPE_REFERENCE)
                                   : null;

        if (!(type instanceof IGenericInstance)) {
            return argument;
        }

        final CastExpression cast = new CastExpression(_astBuilder.convertType(type), argument);

        cast.putUserData(Keys.RESULT_CAST, true);
        return cast;
    }

    /**
     * Returns the bounds to which the arguments were widened.  None is known of the result when another
     * argument is passed raw, as the compiler then erases it, or when no argument tells the compiler
     * what one of the type variables stands for.
     */
    private static List<TypeReference> widenedBounds(final MethodDefinition method, final List<Expression> arguments) {
        final List<TypeReference> bounds = new ArrayList<>();

        if (any(method.getGenericParameters(), v -> !isInferredFromArguments(v, method))) {
            return bounds;
        }

        for (int i = 0; i < arguments.size(); i++) {
            final TypeReference bound = arguments.get(i).getUserData(Keys.SELECTS_OVERLOAD);

            if (bound != null) {
                if (!method.getParameters().get(i).getParameterType().isGenericParameter()) {
                    return Collections.emptyList();
                }

                bounds.add(bound);
            }
        }

        return bounds;
    }

    /**
     * Returns the type for which the type variable of a result stands, given the type of that result,
     * which may be an array of it.
     */
    private static TypeReference resultTypeArgument(final TypeReference declared, final TypeReference result) {
        TypeReference variable = declared;
        TypeReference argument = result;

        while (variable.isArray() && argument.isArray()) {
            variable = variable.getElementType();
            argument = argument.getElementType();
        }

        return MetadataHelper.getBoxedTypeOrSelf(argument);
    }

    /**
     * Returns the types for which the type variables of a class stand, as far as the parameters and the
     * result of one of its methods determine them.
     */
    private static Map<String, TypeReference> receiverTypeArguments(
        final MethodReference method,
        final IMethodSignature implemented,
        final TypeReference functionType) {

        final MethodDefinition definition = method.resolve();
        final Map<String, TypeReference> arguments = new HashMap<>();
        final List<ParameterDefinition> declared = definition.getParameters();
        final List<ParameterDefinition> actual = implemented.getParameters();

        for (int i = 0; i < declared.size() && i < actual.size(); i++) {
            addTypeArgument(arguments, declared.get(i).getParameterType(), actual.get(i).getParameterType());
        }

        addTypeArgument(arguments, definition.getReturnType(), implemented.getReturnType());

        // The implemented signature is erased, unlike the types of the functional interface.
        final MethodDefinition descriptor = functionType != null ? descriptorOf(functionType.resolve()) : null;
        final TypeReference result = descriptor != null
                                     ? typeArgumentIn(descriptor.getReturnType(), descriptor, functionType)
                                     : null;

        if (result != null) {
            addNestedTypeArguments(arguments, definition.getReturnType(), result);
        }

        for (int i = 0; descriptor != null && i < declared.size() && i < descriptor.getParameters().size(); i++) {
            final TypeReference actualType = typeArgumentIn(descriptor.getParameters().get(i).getParameterType(),
                                                            descriptor,
                                                            functionType);

            if (actualType != null) {
                addNestedTypeArguments(arguments, declared.get(i).getParameterType(), actualType);
            }
        }

        return arguments;
    }

    private static void addNestedTypeArguments(
        final Map<String, TypeReference> arguments,
        final TypeReference declared,
        final TypeReference actual) {

        if (!(declared instanceof IGenericInstance)) {
            return;
        }

        // The actual type may inherit its type arguments from a supertype.
        final TypeDefinition definition = declared.resolve();
        final TypeReference viewed = definition != null ? MetadataHelper.asSuper(definition, actual) : null;

        if (!(viewed instanceof IGenericInstance)) {
            return;
        }

        final List<TypeReference> targets = ((IGenericInstance) declared).getTypeArguments();
        final List<TypeReference> sources = ((IGenericInstance) viewed).getTypeArguments();

        for (int i = 0, n = Math.min(targets.size(), sources.size()); i < n; i++) {
            final TypeReference source = sources.get(i);

            TypeReference target = targets.get(i);
            TypeReference element = source;

            // The bounds of matching wildcards tell as much as the types themselves.
            if (element.isWildcardType() && target.isWildcardType() &&
                element.hasSuperBound() == target.hasSuperBound() &&
                wildcardBound(element) != null && wildcardBound(target) != null) {

                element = wildcardBound(element);
                target = wildcardBound(target);
            }

            if (element.isWildcardType() || element.isGenericParameter()) {
                continue;
            }

            while (target.isArray() && element.isArray()) {
                target = target.getElementType();
                element = element.getElementType();
            }

            if (target.isGenericParameter()) {
                arguments.put(target.getSimpleName(), element);
            }
            else {
                addNestedTypeArguments(arguments, target, element);
            }
        }
    }

    private static void addTypeArgument(
        final Map<String, TypeReference> arguments,
        final TypeReference declared,
        final TypeReference actual) {

        TypeReference variable = declared;

        while (variable.isArray()) {
            variable = variable.getElementType();
        }

        if (variable.isGenericParameter()) {
            arguments.put(variable.getSimpleName(), resultTypeArgument(declared, actual));
        }
    }

    /**
     * Returns the types for which the type variables of a class stand, as far as the arguments of one of
     * its methods determine them.
     */
    private Map<String, TypeReference> argumentTypeArguments(
        final MethodReference method,
        final List<Expression> arguments) {

        final MethodDefinition definition = method.resolve();
        final JavaResolver resolver = new JavaResolver(_context);
        final Map<String, TypeReference> typeArguments = new HashMap<>();

        for (int i = 0; i < arguments.size() && i < definition.getParameters().size(); i++) {
            final ResolveResult argument = resolver.apply(arguments.get(i));

            if (argument != null && argument.getType() != null) {
                final TypeReference declared = definition.getParameters().get(i).getParameterType();

                addTypeArgument(typeArguments, declared, argument.getType());
                addNestedTypeArguments(typeArguments, declared, argument.getType());
            }
        }

        return typeArguments;
    }

    private boolean specializeReceiver(
        final MethodGroupExpression methodGroup,
        final TypeReference declaringType,
        final Map<String, TypeReference> arguments,
        final String required) {

        final Expression target = methodGroup.getTarget();

        target.remove();

        final Expression specialized = specializedReceiver(target, declaringType, arguments, required);

        if (specialized == target) {
            methodGroup.setTarget(target);
            return false;
        }

        methodGroup.setTarget(new ParenthesizedExpression(specialized));
        return true;
    }

    /**
     * Casts a receiver, through its raw type, to its class with the given arguments for its type
     * variables, if the required one is among those which accept theirs.  Any other type argument is left
     * unknown.
     */
    private Expression specializedReceiver(
        final Expression receiver,
        final TypeReference declaringType,
        final Map<String, TypeReference> arguments,
        final String required) {

        final TypeDefinition definition = declaringType.resolve();

        if (definition == null) {
            return receiver;
        }

        final List<TypeReference> typeArguments = new ArrayList<>();
        boolean specialized = false;

        for (final GenericParameter variable : definition.getGenericParameters()) {
            final TypeReference typeArgument = arguments.get(variable.getSimpleName());

            if (typeArgument != null && !typeArgument.isPrimitive() && accepts(variable, typeArgument)) {
                typeArguments.add(typeArgument);
                specialized |= variable.getSimpleName().equals(required);
            }
            else {
                typeArguments.add(com.strobel.assembler.metadata.WildcardType.unbounded());
            }
        }

        if (!specialized) {
            return receiver;
        }

        final CastExpression raw = new CastExpression(
            _astBuilder.convertType(MetadataHelper.erase(definition)),
            receiver
        );

        final CastExpression cast = new CastExpression(
            _astBuilder.convertType(definition.makeGenericType(typeArguments)),
            raw
        );

        raw.putUserData(Keys.RESULT_CAST, true);
        cast.putUserData(Keys.RESULT_CAST, true);
        cast.putUserData(Keys.WIDENED_BOUNDS, receiver.getUserData(Keys.WIDENED_BOUNDS));
        return cast;
    }

    /** Returns the type variables of its class which a method throws. */
    private static List<GenericParameter> thrownClassVariables(final MethodReference method) {
        final MethodDefinition definition = method.resolve();
        final List<GenericParameter> variables = new ArrayList<>();

        if (definition != null) {
            for (final GenericParameter variable : definition.getDeclaringType().getGenericParameters()) {
                if (any(definition.getThrownTypes(), t -> t.isGenericParameter() &&
                                                          t.getSimpleName().equals(variable.getSimpleName()))) {

                    variables.add(variable);
                }
            }
        }

        return variables;
    }

    /**
     * Returns the exceptions for which the type variables thrown by an invoked method may stand: those
     * caught around the invocation, or else those declared by the method being decompiled.
     */
    private Map<String, TypeReference> thrownTypeArguments(final MethodReference method, final int offset) {
        final List<TypeReference> candidates = caughtTypes(offset);
        final List<TypeReference> checked = new ArrayList<>();

        for (final TypeReference caught : candidates) {
            if (!isUnchecked(caught)) {
                checked.add(caught);
            }
        }

        candidates.addAll(_context.getCurrentMethod().getThrownTypes());

        final List<GenericParameter> variables = thrownClassVariables(method);

        // A single variable must stand for a type which leaves every handler reachable.
        if (variables.size() == 1 && checked.size() > 1) {
            TypeReference covering = firstOrDefault(
                candidates,
                candidate -> all(checked, caught -> MetadataHelper.isAssignableFrom(candidate, caught))
            );

            if (covering == null) {
                covering = checked.get(0);

                for (final TypeReference caught : checked) {
                    covering = MetadataHelper.findCommonSuperType(covering, caught);
                }
            }

            candidates.add(0, covering);
        }

        return assignExceptions(variables, candidates);
    }

    /**
     * Returns the exceptions of a functional interface for which the type variables thrown by a
     * referenced method may stand.
     */
    private static Map<String, TypeReference> thrownByDescriptor(
        final MethodReference method,
        final TypeReference functionType) {

        final MethodDefinition descriptor = functionType != null ? descriptorOf(functionType.resolve()) : null;
        final List<TypeReference> candidates = new ArrayList<>();

        if (descriptor != null) {
            for (final TypeReference declared : descriptor.getThrownTypes()) {
                final TypeReference thrown = typeArgumentIn(declared, descriptor, functionType);

                if (thrown != null) {
                    candidates.add(thrown);
                }
            }
        }

        return assignExceptions(thrownClassVariables(method), candidates);
    }

    /**
     * Gives each variable an exception which it accepts, a different one where possible.  Any of them
     * will do, as a cast through a raw type is not checked.
     */
    private static Map<String, TypeReference> assignExceptions(
        final List<GenericParameter> variables,
        final List<TypeReference> candidates) {

        final Map<String, TypeReference> assigned = new LinkedHashMap<>();

        for (final GenericParameter variable : variables) {
            TypeReference exception = firstOrDefault(
                candidates,
                candidate -> accepts(variable, candidate) && !assigned.containsValue(candidate)
            );

            if (exception == null) {
                exception = firstOrDefault(candidates, candidate -> accepts(variable, candidate));
            }

            if (exception != null) {
                assigned.put(variable.getSimpleName(), exception);
            }
        }

        return assigned;
    }

    /**
     * Returns a type thrown by the descriptor of a functional interface as seen from that interface, or
     * {@code null} if it is a type variable without a type argument.
     */
    private static TypeReference typeArgumentIn(
        final TypeReference type,
        final MethodDefinition descriptor,
        final TypeReference functionType) {

        if (!type.isGenericParameter()) {
            if (!type.containsGenericParameters()) {
                return type;
            }

            final TypeReference declaring = MetadataHelper.asSuper(descriptor.getDeclaringType(), functionType);

            if (!(declaring instanceof IGenericInstance)) {
                return null;
            }

            final Map<TypeReference, TypeReference> substitutions = new HashMap<>();
            final List<GenericParameter> parameters = descriptor.getDeclaringType().getGenericParameters();
            final List<TypeReference> typeArguments = ((IGenericInstance) declaring).getTypeArguments();

            for (int i = 0; i < parameters.size() && i < typeArguments.size(); i++) {
                substitutions.put(parameters.get(i), typeArguments.get(i));
            }

            return TypeSubstitutionVisitor.instance().visit(type, substitutions);
        }

        final TypeReference declaring = MetadataHelper.asSuper(descriptor.getDeclaringType(), functionType);

        if (!(declaring instanceof IGenericInstance)) {
            return null;
        }

        final List<GenericParameter> variables = descriptor.getDeclaringType().getGenericParameters();
        final List<TypeReference> arguments = ((IGenericInstance) declaring).getTypeArguments();

        for (int i = 0; i < variables.size() && i < arguments.size(); i++) {
            final TypeReference argument = arguments.get(i);

            if (variables.get(i).getSimpleName().equals(type.getSimpleName()) &&
                !argument.isWildcardType() &&
                !argument.isGenericParameter()) {

                return argument;
            }
        }

        return null;
    }

    /** Returns the abstract method of a functional interface, which it may inherit. */
    private static MethodDefinition descriptorOf(final TypeDefinition type) {
        if (type == null) {
            return null;
        }

        for (final MethodDefinition method : type.getDeclaredMethods()) {
            if (method.isAbstract()) {
                return method;
            }
        }

        for (final TypeReference superInterface : type.getExplicitInterfaces()) {
            final MethodDefinition inherited = descriptorOf(superInterface.resolve());

            if (inherited != null) {
                return inherited;
            }
        }

        return null;
    }

    private static boolean isUnchecked(final TypeReference exception) {
        for (TypeDefinition type = exception.resolve(); type != null; ) {
            final String name = type.getInternalName();

            if ("java/lang/RuntimeException".equals(name) || "java/lang/Error".equals(name)) {
                return true;
            }

            final TypeReference baseType = type.getBaseType();

            type = baseType != null ? baseType.resolve() : null;
        }

        return false;
    }

    /** Returns the types of the handlers around an instruction, innermost first. */
    private List<TypeReference> caughtTypes(final int offset) {
        final MethodBody body = _context.getCurrentMethod().getBody();
        final List<TypeReference> types = new ArrayList<>();

        if (body != null) {
            for (final ExceptionHandler handler : body.getExceptionHandlers()) {
                if (handler.isCatch() &&
                    handler.getTryBlock().getFirstInstruction().getOffset() <= offset &&
                    offset <= handler.getTryBlock().getLastInstruction().getOffset()) {

                    types.add(handler.getCatchType());
                }
            }
        }

        return types;
    }

    /**
     * Casts a method reference to its raw functional interface, whose result accepts any type.  Where
     * the method takes arguments, their types are first kept by naming only the result type as the bound
     * of its variable.
     */
    private Expression adaptReturnType(
        final MethodGroupExpression methodGroup,
        final MethodReference method,
        final IGenericInstance functionType) {

        final TypeReference type = (TypeReference) functionType;
        final TypeReference accepting = method.getParameters().isEmpty() ? null : withObjectResult(type);

        Expression value = methodGroup;

        if (accepting != null) {
            final CastExpression cast = new CastExpression(_astBuilder.convertType(accepting), value);

            cast.putUserData(Keys.RESULT_CAST, true);
            value = cast;
        }

        final CastExpression cast = new CastExpression(_astBuilder.convertType(MetadataHelper.erase(type)), value);

        cast.putUserData(Keys.RESULT_CAST, true);
        return cast;
    }

    /**
     * Returns a functional interface with the type argument of its result replaced by the bound of its
     * variable, or {@code null} if its result is not declared as a variable with such a bound.
     */
    private static TypeReference withObjectResult(final TypeReference type) {
        final TypeDefinition definition = type != null ? type.resolve() : null;

        if (definition == null) {
            return null;
        }

        final List<TypeReference> arguments = new ArrayList<>(((IGenericInstance) type).getTypeArguments());
        final List<GenericParameter> variables = definition.getGenericParameters();

        for (final MethodDefinition method : definition.getDeclaredMethods()) {
            final TypeReference returned = method.getReturnType();

            if (!method.isAbstract() || !returned.isGenericParameter()) {
                continue;
            }

            for (int i = 0; i < variables.size() && i < arguments.size(); i++) {
                final GenericParameter variable = variables.get(i);

                if (variable.getSimpleName().equals(returned.getSimpleName())) {
                    final TypeReference bound = variable.hasExtendsBound() ? variable.getExtendsBound()
                                                                           : BuiltinTypes.Object;

                    // A bound which depends on type variables cannot be named here.
                    if (bound.isGenericParameter() || bound.containsGenericParameters()) {
                        return null;
                    }

                    arguments.set(i, bound);
                    return definition.makeGenericType(arguments);
                }
            }
        }

        return null;
    }

    /**
     * A raw receiver erases the parameters typed by its class variables, so that another overload could
     * take the arguments: they are cast to those erasures.
     */
    private void keepMemberSelection(final MethodReference method, final List<Expression> arguments) {
        final MethodDefinition definition = method.resolve();

        if (definition == null || overloads(definition).size() < 2) {
            return;
        }

        final List<ParameterDefinition> parameters = definition.getParameters();

        for (int i = 0; i < arguments.size() && i < parameters.size(); i++) {
            final TypeReference declared = parameters.get(i).getParameterType();

            TypeReference element = declared;
            int rank = 0;

            while (element.isArray()) {
                element = element.getElementType();
                rank++;
            }

            final boolean typed = element.isGenericParameter()
                                  ? ((GenericParameter) element).getOwner() instanceof TypeReference
                                  : element instanceof IGenericInstance && mentionsAny(element, definition);

            if (typed) {
                TypeReference erased = element.isGenericParameter()
                                       ? MetadataHelper.erase(MetadataHelper.getUpperBound(element))
                                       : MetadataHelper.erase(element);

                while (rank-- > 0) {
                    erased = erased.makeArrayType();
                }

                final CastExpression cast = new CastExpression(_astBuilder.convertType(erased), arguments.get(i));

                cast.putUserData(Keys.RESULT_CAST, true);
                arguments.set(i, cast);
            }
        }
    }

    private static boolean isOverloadedOnClassVariables(final MethodReference method) {
        return takesClassTypeVariables(method) && overloads(method.resolve()).size() > 1;
    }

    /** Tells whether a type mentions a type variable of the class declaring a method. */
    private static boolean mentionsAny(final TypeReference type, final MethodDefinition method) {
        return any(method.getDeclaringType().getGenericParameters(),
                   variable -> mentions(type, variable.getSimpleName()));
    }

    /** Tells whether the result of a method is a type with wildcard type arguments. */
    private static boolean hasWildcardResult(final Expression receiver) {
        final Expression invocation = receiver.getUserData(Keys.RESULT_CAST) != null
                                      ? ((CastExpression) receiver).getExpression()
                                      : receiver;
        final TypeReference declared = declaredTypeOf(invocation);

        return declared instanceof IGenericInstance &&
               any(((IGenericInstance) declared).getTypeArguments(), TypeReference::isWildcardType);
    }

    /**
     * Returns the receiver of a field write specialized with the type of the value, or else made raw.
     */
    private Expression writableReceiver(final Expression receiver, final FieldReference field, final Expression value) {
        final ResolveResult resolved = new JavaResolver(_context).apply(value);
        final TypeReference declared = declaredType(field);

        if (resolved != null && resolved.getType() != null && declared != null && declared.isGenericParameter()) {
            final Expression discarded = discardResultCast(receiver);
            final Expression specialized = specializedReceiver(
                discarded,
                field.getDeclaringType(),
                Collections.singletonMap(declared.getSimpleName(), resolved.getType()),
                declared.getSimpleName()
            );

            return specialized != discarded ? specialized : rawReceiver(discarded, field.getDeclaringType());
        }

        return rawReceiver(receiver, field.getDeclaringType());
    }

    private Expression rawReceiver(final Expression receiver, final TypeReference declaringType) {
        if (receiver.getUserData(Keys.RESULT_CAST) != null) {
            receiver.putUserData(Keys.WIDENED_BOUNDS, Collections.<TypeReference>emptyList());
            return receiver;
        }

        final CastExpression cast = new CastExpression(
            _astBuilder.convertType(MetadataHelper.erase(declaringType)),
            receiver
        );

        cast.putUserData(Keys.RESULT_CAST, true);
        cast.putUserData(Keys.WIDENED_BOUNDS, Collections.<TypeReference>emptyList());
        return cast;
    }

    /** Tells whether the parameters of a method depend on the type variables of its class. */
    private static boolean takesClassTypeVariables(final MethodReference method) {
        final MethodDefinition definition = method.resolve();

        return definition != null &&
               any(
                   definition.getDeclaringType().getGenericParameters(),
                   variable -> any(definition.getParameters(),
                                   p -> mentions(p.getParameterType(), variable.getSimpleName()))
               );
    }

    /** Tells whether the type of a field depends on the type variables of its class. */
    private static boolean isTypedByClassVariables(final FieldReference field) {
        final FieldDefinition definition = field.resolve();

        return definition != null &&
               any(definition.getDeclaringType().getGenericParameters(),
                   variable -> mentions(definition.getFieldType(), variable.getSimpleName()));
    }

    /** Marks an expression accessing a value whose type depends on a widened argument. */
    private static void markWidened(final Expression expression, final Expression source) {
        expression.putUserData(Keys.WIDENED_RECEIVER, true);
        expression.putUserData(Keys.WIDENED_BOUNDS, source.getUserData(Keys.WIDENED_BOUNDS));
    }

    /** Tells whether the type of an expression depends on a widened argument. */
    private static boolean isWidened(final Expression expression) {
        if (expression instanceof ConditionalExpression) {
            final ConditionalExpression conditional = (ConditionalExpression) expression;

            return isWidened(conditional.getTrueExpression()) || isWidened(conditional.getFalseExpression());
        }

        return expression.getUserData(Keys.RESULT_CAST) != null ||
               expression.getUserData(Keys.WIDENED_RECEIVER) != null;
    }

    /**
     * Returns the nearest generic superclass or interface of a type which can be named here, as a raw
     * type, or {@code null}.
     */
    private TypeReference accessibleGenericSupertype(final TypeReference type) {
        final List<TypeDefinition> classes = new ArrayList<>();

        for (TypeDefinition definition = type.resolve(); definition != null; ) {
            classes.add(definition);

            final TypeReference baseType = definition.getBaseType();

            definition = baseType != null ? baseType.resolve() : null;
        }

        final List<TypeReference> supertypes = new ArrayList<TypeReference>(classes);

        for (final TypeDefinition definition : classes) {
            supertypes.addAll(definition.getExplicitInterfaces());
        }

        for (final TypeReference supertype : supertypes) {
            final TypeDefinition definition = supertype.resolve();

            if (definition != null && definition.isGenericDefinition() && isAccessibleType(definition)) {
                return MetadataHelper.erase(definition);
            }
        }

        return null;
    }

    /**
     * A value which depends on a widened result, such as its iterator, one of its fields or an element
     * of its array, no longer has the parameterized type expected where it is stored, returned or passed.
     */
    private Expression eraseConsumedResult(final Expression value) {
        if (value instanceof ConditionalExpression) {
            final ConditionalExpression conditional = (ConditionalExpression) value;
            final Expression whenTrue = conditional.getTrueExpression();
            final Expression whenFalse = conditional.getFalseExpression();

            whenTrue.remove();
            whenFalse.remove();
            conditional.setTrueExpression(eraseConsumedResult(whenTrue));
            conditional.setFalseExpression(eraseConsumedResult(whenFalse));

            return conditional;
        }

        if (value.getUserData(Keys.WIDENED_RECEIVER) == null) {
            return value;
        }

        final TypeReference declared = declaredTypeOf(value);

        return declared != null ? eraseParameterizedResult(declared, value) : value;
    }

    /**
     * Returns the type with which the value of a member, or of an element of the array which it holds,
     * is declared, if it is known.
     */
    private static TypeReference declaredTypeOf(final Expression value) {
        Expression source = value;
        int depth = 0;

        while (source instanceof IndexerExpression) {
            source = ((IndexerExpression) source).getTarget();
            depth++;
        }

        TypeReference declared = declaredType(source.getUserData(Keys.MEMBER_REFERENCE));

        while (declared != null && depth-- > 0 && declared.isArray()) {
            declared = declared.getElementType();
        }

        return declared;
    }

    /** Returns the type with which a field or the result of a method is declared, if it is known. */
    private static TypeReference declaredType(final MemberReference member) {
        if (member instanceof MethodReference) {
            final MethodDefinition method = ((MethodReference) member).resolve();

            return method != null ? method.getReturnType() : null;
        }

        final FieldDefinition field = member instanceof FieldReference ? ((FieldReference) member).resolve() : null;

        return field != null ? field.getFieldType() : null;
    }

    /**
     * Removes the cast to a raw type where the result is discarded, as a cast cannot stand as a statement,
     * or where its members or elements are accessed, as their types would be erased too.
     */
    private static Expression discardResultCast(final Expression expression) {
        if (expression instanceof ConditionalExpression) {
            final ConditionalExpression conditional = (ConditionalExpression) expression;
            final Expression whenTrue = conditional.getTrueExpression();
            final Expression whenFalse = conditional.getFalseExpression();

            whenTrue.remove();
            whenFalse.remove();

            final Expression first = discardResultCast(whenTrue);
            final Expression second = discardResultCast(whenFalse);
            final List<TypeReference> bounds = first.getUserData(Keys.WIDENED_BOUNDS);

            conditional.setTrueExpression(first);
            conditional.setFalseExpression(second);
            conditional.putUserData(Keys.WIDENED_BOUNDS,
                                    bounds != null ? bounds : second.getUserData(Keys.WIDENED_BOUNDS));

            return conditional;
        }

        if (expression.getUserData(Keys.RESULT_CAST) == null) {
            return expression;
        }

        final Expression invocation = ((CastExpression) expression).getExpression();

        invocation.remove();
        return invocation;
    }

    @SuppressWarnings("UnusedParameters")
    private List<AstType> convertTypeArguments(final MethodReference methodReference) {
        return _context.getSettings().getForceExplicitTypeArguments() ? explicitTypeArguments(methodReference)
                                                                     : Collections.<AstType>emptyList();
    }

    private List<AstType> explicitTypeArguments(final MethodReference methodReference) {
        if (methodReference instanceof IGenericInstance) {
            final List<TypeReference> typeArguments = ((IGenericInstance) methodReference).getTypeArguments();

            if (!typeArguments.isEmpty()) {
                final List<AstType> astTypeArguments = new ArrayList<>();

                for (final TypeReference type : typeArguments) {
                    astTypeArguments.add(_astBuilder.convertType(type));
                }

                return astTypeArguments;
            }
        }

        return Collections.emptyList();
    }

    /**
     * Returns the explicit type arguments of a call with widened arguments: the type variables of the
     * widened parameters take the types of the casts, while the others are kept.  They are needed when
     * forced, or when no argument lets the compiler infer one of them.
     */
    private List<AstType> widenedTypeArguments(final MethodReference method, final List<Expression> arguments) {
        final MethodDefinition definition = method.resolve();
        final List<GenericParameter> variables = definition.getGenericParameters();
        final boolean needed = _context.getSettings().getForceExplicitTypeArguments() ||
                               any(variables, v -> !isInferredFromArguments(v, definition));
        final List<AstType> typeArguments = needed ? explicitTypeArguments(method)
                                                   : Collections.<AstType>emptyList();

        if (typeArguments.size() != variables.size()) {
            return Collections.emptyList();
        }

        for (int i = 0; i < arguments.size(); i++) {
            final TypeReference bound = arguments.get(i).getUserData(Keys.SELECTS_OVERLOAD);

            if (bound == null || !definition.getParameters().get(i).getParameterType().isGenericParameter()) {
                continue;
            }

            // An intersection of types cannot be written as a type argument.
            if (bound instanceof ICompoundType) {
                return Collections.emptyList();
            }

            TypeReference type = definition.getParameters().get(i).getParameterType();

            while (type.isGenericParameter()) {
                final String name = type.getSimpleName();

                for (int j = 0; j < variables.size(); j++) {
                    if (variables.get(j).getSimpleName().equals(name)) {
                        typeArguments.set(j, _astBuilder.convertType(bound));
                    }
                }

                if (!((GenericParameter) type).hasExtendsBound()) {
                    break;
                }

                type = ((GenericParameter) type).getExtendsBound();

                // The erasure of a parameterized bound is not within that bound.
                if (type instanceof IGenericInstance) {
                    return Collections.emptyList();
                }
            }
        }

        // Types which depend on a widened variable are left to the inference of the compiler.
        return dependentVariables(definition, arguments).isEmpty() ? typeArguments
                                                                   : Collections.<AstType>emptyList();
    }

    /** Returns the type variables of a method whose parameterized bounds mention a widened one. */
    private static List<GenericParameter> dependentVariables(
        final MethodDefinition method,
        final List<Expression> arguments) {

        final Set<String> widened = new HashSet<>();

        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i).getUserData(Keys.SELECTS_OVERLOAD) != null) {
                widened.addAll(variableChain(method.getParameters().get(i).getParameterType()));
            }
        }

        final List<GenericParameter> dependent = new ArrayList<>();

        for (final GenericParameter variable : method.getGenericParameters()) {
            if (!widened.contains(variable.getSimpleName()) &&
                variable.hasExtendsBound() &&
                variable.getExtendsBound() instanceof IGenericInstance &&
                any(widened, name -> mentions(variable.getExtendsBound(), name))) {

                dependent.add(variable);
            }
        }

        return dependent;
    }

    /** Returns the bound of a result whose type depends on a widened variable, or {@code null}. */
    private static TypeReference dependentResultBound(
        final MethodDefinition method,
        final List<Expression> arguments) {

        final TypeReference returned = method.getReturnType();

        for (final GenericParameter variable : dependentVariables(method, arguments)) {
            if (returned.isGenericParameter() && returned.getSimpleName().equals(variable.getSimpleName())) {
                return variable.getExtendsBound();
            }
        }

        return null;
    }

    private static boolean isInferredFromArguments(final GenericParameter variable, final MethodDefinition method) {
        final String name = variable.getSimpleName();

        return any(method.getParameters(), p -> mentions(p.getParameterType(), name));
    }

    /** Tells whether a type mentions a type variable, directly or as a bound of another one. */
    private static boolean mentions(final TypeReference type, final String name) {
        if (type.isGenericParameter()) {
            final GenericParameter parameter = (GenericParameter) type;

            return parameter.getSimpleName().equals(name) ||
                   parameter.hasExtendsBound() &&
                   parameter.getExtendsBound().isGenericParameter() &&
                   mentions(parameter.getExtendsBound(), name);
        }

        if (type.isArray()) {
            return mentions(type.getElementType(), name);
        }

        if (type.isWildcardType()) {
            return type.hasSuperBound() ? mentions(type.getSuperBound(), name)
                                        : type.hasExtendsBound() && mentions(type.getExtendsBound(), name);
        }

        return type instanceof IGenericInstance &&
               any(((IGenericInstance) type).getTypeArguments(), argument -> mentions(argument, name));
    }

    @SuppressWarnings("UnusedParameters")
    private List<Expression> adjustArgumentsForMethodCall(
        final MethodReference method,
        final Expression receiver,
        final List<Expression> arguments) {

        if (!arguments.isEmpty() && method.isConstructor()) {
            final TypeDefinition resolvedType = method.getDeclaringType().resolve();

            if (resolvedType != null && !resolvedType.isEnum()) {
                if (resolvedType.isLocalClass()) {
                    return arguments;
                }

                final MethodDefinition resolvedMethod = method.resolve();

                if (resolvedMethod != null &&
                    resolvedMethod.isSynthetic() &&
                    (resolvedMethod.getFlags() & Flags.AccessFlags) == 0) {

                    //
                    // This is an accessor which lets other classes of the nest call a private constructor.
                    // The constructor may belong to a nested class or to the top-level class itself.
                    //

                    final List<ParameterDefinition> parameters = resolvedMethod.getParameters();

                    int start = 0;
                    int end = arguments.size();

                    for (int i = parameters.size() - 1; i >= 0; i--) {
                        final TypeReference parameterType = parameters.get(i).getParameterType();
                        final TypeDefinition resolvedParameterType = parameterType.resolve();

                        if (resolvedParameterType != null && resolvedParameterType.isAnonymous()) {
                            --end;
                        }
                        else {
                            break;
                        }
                    }

                    if (resolvedType.isNested() &&
                        !resolvedType.isStatic() &&
                        !_context.getSettings().getShowSyntheticMembers()) {

                        ++start;
                    }

                    start = Math.min(start, end);

                    return adjustArgumentsForMethodCallCore(
                        method,
                        receiver,
                        method.getParameters().subList(start, end),
                        arguments.subList(start, end)
                    );
                }
            }
        }

        return adjustArgumentsForMethodCallCore(method, receiver, method.getParameters(), arguments);
    }

    private List<Expression> adjustArgumentsForMethodCallCore(
        final MethodReference method,
        final Expression receiver,
        final List<ParameterDefinition> parameters,
        final List<Expression> arguments) {

        final int parameterCount = parameters.size();

        assert parameterCount == arguments.size();

        final JavaResolver resolver = new JavaResolver(_context);
        final ConvertTypeOptions options = new ConvertTypeOptions();
        for (int i = 0, n = arguments.size(); i < n; i++) {
            arguments.set(i, eraseConsumedResult(arguments.get(i)));
        }

        final List<Expression> originals = new ArrayList<>(arguments);

        options.setAllowWildcards(false);

        final Map<Integer, TypeReference> argumentTypes = new HashMap<>();

        for (int i = 0, n = arguments.size(); i < n; i++) {
            final ResolveResult result = resolver.apply(arguments.get(i));

            if (result != null && result.getType() != null) {
                argumentTypes.put(parameters.get(i).getPosition(), result.getType());
            }
        }

        for (int i = 0, n = arguments.size(); i < n; i++) {
            final Expression argument = arguments.get(i);
            final ResolveResult resolvedArgument = resolver.apply(argument);

            if (resolvedArgument == null ||
                argument instanceof LambdaExpression /*||
                argument instanceof MethodGroupExpression*/) {

                continue;
            }

            final ParameterDefinition p = parameters.get(i);

            final TypeReference aType = resolvedArgument.getType();
            final TypeReference pType = p.getParameterType();

            final ResolveResult resolvedReceiver = receiver != null ? resolver.apply(receiver) : null;
            final TypeReference boundType = overloadSelectingBound(
                method,
                resolvedReceiver != null ? resolvedReceiver.getType() : null,
                p.getPosition(),
                argumentTypes
            );

            if (boundType != null) {
                final ConvertTypeOptions boundOptions = new ConvertTypeOptions();
                boundOptions.setIncludeIntersectionTypes(true);
                final CastExpression cast = new CastExpression(_astBuilder.convertType(boundType, boundOptions), argument);
                cast.putUserData(Keys.SELECTS_OVERLOAD, boundType);
                arguments.set(i, cast);
            }
            else if (isCastRequired(pType, aType, true) &&
                     !isForeignTypeVariable(pType) &&
                     !isTypedByTarget(method, argument, pType, aType)) {

                arguments.set(
                    i,
                    new CastExpression(_astBuilder.convertType(pType, options), argument)
                );
            }
        }

        reconcileDependentArguments(method, parameters, originals, argumentTypes, arguments);

        int first = 0, last = parameterCount - 1;

        while (first < parameterCount && parameters.get(first).isSynthetic()) {
            ++first;
        }

        while (last >= 0 && parameters.get(last).isSynthetic()) {
            --last;
        }

        if (first >= parameterCount || last < 0) {
            return Collections.emptyList();
        }

        if (first == 0 && last == parameterCount - 1) {
            return arguments;
        }

        return arguments.subList(first, last + 1);
    }

    /**
     * Tells whether an argument takes its type from the parameter it is passed to, so that casting it
     * is not wanted.  The type arguments of such a cast come from the bytecode and are only known up
     * to erasure: {@code collect((Collector<? super Object, ?, List<String>>) Collectors.toList())}
     * does not compile where {@code collect(Collectors.toList())} does.
     * <p>
     * The cast is kept when it can carry some meaning: when the invoked method is overloaded, when a
     * method reference adapts the return type of its target, or when the compiler would accept it, as
     * it may then be what selects the type arguments.
     */
    private static boolean isTypedByTarget(
        final MethodReference method,
        final Expression argument,
        final TypeReference parameterType,
        final TypeReference argumentType) {

        if (!(parameterType instanceof IGenericInstance) ||
            !MetadataHelper.isAssignableFrom(MetadataHelper.erase(parameterType), MetadataHelper.erase(argumentType)) ||
            isOverloaded(method)) {

            return false;
        }

        if (argument instanceof MethodGroupExpression) {
            return !isOverloaded((MethodReference) argument.getUserData(Keys.MEMBER_REFERENCE)) &&
                   !adaptsReturnType((MethodGroupExpression) argument);
        }

        if (argument instanceof InvocationExpression) {
            final MemberReference invoked = argument.getUserData(Keys.MEMBER_REFERENCE);

            if (invoked instanceof MethodReference) {
                final MethodDefinition definition = ((MethodReference) invoked).resolve();

                if (definition == null) {
                    return false;
                }

                return !isLegalCast(
                    parameterType,
                    definition.isGenericMethod() ? instantiateWithBounds(definition) : argumentType
                );
            }
        }

        return false;
    }

    /**
     * Tells whether a method reference implements a function returning something else than what the
     * referenced method returns, e.g. {@code (Function<String, Object>) String::length}.
     */
    private static boolean adaptsReturnType(final MethodGroupExpression methodGroup) {
        final DynamicCallSite callSite = methodGroup.getUserData(Keys.DYNAMIC_CALL_SITE);
        final MethodReference target = (MethodReference) methodGroup.getUserData(Keys.MEMBER_REFERENCE);

        if (callSite == null || target == null ||
            callSite.getBootstrapArguments().size() < 3 ||
            !(callSite.getBootstrapArguments().get(2) instanceof IMethodSignature)) {

            return true;
        }

        final TypeReference implemented = ((IMethodSignature) callSite.getBootstrapArguments().get(2)).getReturnType();

        if (implemented.isPrimitive()) {
            return false;
        }

        final TypeReference returned = target.isConstructor() ? target.getDeclaringType()
                                                              : target.getReturnType();

        return !MetadataHelper.isSameType(
            MetadataHelper.erase(implemented),
            MetadataHelper.erase(MetadataHelper.getBoxedTypeOrSelf(returned))
        );
    }

    /**
     * Returns the type of a generic method invocation which nothing but the bounds of the type
     * variables constrains, as in the operand of a cast.
     */
    private static TypeReference instantiateWithBounds(final MethodDefinition method) {
        final Map<TypeReference, TypeReference> mappings = new HashMap<>();

        for (final GenericParameter parameter : method.getGenericParameters()) {
            mappings.put(parameter, MetadataHelper.eraseRecursive(parameter));
        }

        return TypeSubstitutionVisitor.instance().visit(method.getReturnType(), mappings);
    }

    /**
     * Tells whether the type arguments of a cast contain those of the type being cast.
     */
    private static boolean isLegalCast(final TypeReference targetType, final TypeReference sourceType) {
        if (!(sourceType instanceof IGenericInstance) ||
            !MetadataHelper.isSameType(MetadataHelper.erase(targetType), MetadataHelper.erase(sourceType))) {

            return true;
        }

        final List<TypeReference> targets = ((IGenericInstance) targetType).getTypeArguments();
        final List<TypeReference> sources = ((IGenericInstance) sourceType).getTypeArguments();

        for (int i = 0, n = Math.min(targets.size(), sources.size()); i < n; i++) {
            final TypeReference target = targets.get(i);
            final TypeReference source = sources.get(i);

            if (source.isWildcardType() || source.isGenericParameter()) {
                continue;
            }

            final boolean contained;

            if (!target.isWildcardType()) {
                contained = target.isGenericParameter() || MetadataHelper.isSameType(target, source);
            }
            else if (target.hasSuperBound()) {
                contained = MetadataHelper.isAssignableFrom(source, target.getSuperBound());
            }
            else {
                contained = !target.hasExtendsBound() ||
                            MetadataHelper.isAssignableFrom(target.getExtendsBound(), source);
            }

            if (!contained) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tells whether a type is a type variable of a generic method other than the one being decompiled.
     * It cannot be named here, so a cast to it would not compile.
     */
    private boolean isForeignTypeVariable(final TypeReference type) {
        return type.isGenericParameter() &&
               ((GenericParameter) type).getOwner() instanceof IMethodSignature &&
               _context.getCurrentMethod().findTypeVariable(type.getSimpleName()) != type;
    }

    /**
     * Returns the type to which an argument of a generic parameter must be cast to select the invoked
     * method, or {@code null} if no cast is needed: the argument may be accepted by the corresponding
     * parameter of another overload, which the compiler would then prefer, e.g. when
     * {@code append(StringBuffer)} delegates to {@code <B extends Appendable> append(B)}.
     */
    private TypeReference overloadSelectingBound(
        final MethodReference method,
        final TypeReference receiverType,
        final int position,
        final Map<Integer, TypeReference> argumentTypes) {

        final TypeReference argumentType = argumentTypes.get(position);

        final MethodDefinition definition = method.isConstructor() ? null : method.resolve();

        if (definition == null) {
            return null;
        }

        final TypeReference declared = definition.getParameters().get(position).getParameterType();

        if (!isForeignTypeVariable(declared)) {
            return null;
        }

        final String signature = definition.getErasedSignature();
        final List<TypeReference> competing = new ArrayList<>();

        for (final MethodReference candidate : overloads(definition)) {
            final TypeReference other = candidate.getParameters().get(position).getParameterType();

            if (!candidate.getErasedSignature().equals(signature) &&
                !other.isGenericParameter() &&
                isAccessible(candidate) &&
                isAccessibleThrough(candidate, receiverType) &&
                (!isStrict(definition, argumentTypes) || isStrict(candidate, argumentTypes)) &&
                MetadataHelper.isAssignableFrom(MetadataHelper.erase(other), argumentType) &&
                isApplicable(candidate, argumentTypes) &&
                !isLessSpecific(candidate, definition) &&
                (!isThrown((GenericParameter) declared, definition) ||
                 !isExcludedByAnotherCast(candidate, definition, position))) {

                competing.add(MetadataHelper.erase(other));
            }
        }

        if (competing.isEmpty()) {
            return null;
        }

        final TypeReference bound = selectingBound((GenericParameter) declared, definition, receiverType);

        if (isThrown((GenericParameter) declared, definition)) {
            // Casting to the bound would widen what the method throws, so stay close to the argument.
            final TypeReference narrower = accessibleSubstitute(typesOf(bound), argumentType, competing);

            if (narrower != null) {
                return narrower;
            }
        }

        if (bound.isGenericParameter() || all(typesOf(bound), this::isAccessibleType)) {
            return bound;
        }

        return accessibleSubstitute(typesOf(bound), argumentType, competing);
    }

    /**
     * Tells whether an overload accepts the types of all the arguments.  Of a parameter declared with a
     * type variable, only the bounds are examined.
     */
    private static boolean isApplicable(final MethodReference candidate, final Map<Integer, TypeReference> argumentTypes) {
        return all(
                   argumentTypes.entrySet(),
                   argument -> accepts(candidate.getParameters().get(argument.getKey()).getParameterType(),
                                       argument.getValue())
               ) &&
               bindsConsistently(candidate, argumentTypes);
    }

    /** Tells whether a type variable shared by the parameters of a method stands for a single type. */
    private static boolean bindsConsistently(
        final MethodReference candidate,
        final Map<Integer, TypeReference> argumentTypes) {

        final Map<String, TypeReference> bindings = new HashMap<>();
        final List<Map.Entry<GenericParameter, TypeReference>> wildcards = new ArrayList<>();

        for (final Map.Entry<Integer, TypeReference> argument : argumentTypes.entrySet()) {
            TypeReference parameterType = candidate.getParameters().get(argument.getKey()).getParameterType();
            TypeReference argumentType = argument.getValue();

            final boolean array = parameterType.isArray();

            while (parameterType.isArray() && argumentType.isArray()) {
                parameterType = parameterType.getElementType();
                argumentType = argumentType.getElementType();
            }

            // The elements of an array may be of any subtype of the type for which a variable stands.
            if (array && parameterType.isGenericParameter() && !argumentType.isPrimitive()) {
                wildcards.add(new AbstractMap.SimpleEntry<>((GenericParameter) parameterType, argumentType));
            }

            if (!(parameterType instanceof IGenericInstance)) {
                continue;
            }

            final TypeDefinition definition = parameterType.resolve();
            final TypeReference asParameterType = definition != null
                                                  ? MetadataHelper.asSuper(definition, argumentType)
                                                  : null;

            if (!(asParameterType instanceof IGenericInstance)) {
                continue;
            }

            if (!bind((IGenericInstance) parameterType, (IGenericInstance) asParameterType, bindings, wildcards)) {
                return false;
            }
        }

        final Map<String, TypeReference> inferred = new HashMap<>(bindings);

        // An argument of a type variable itself tells what it stands for when nothing else does.
        for (final Map.Entry<Integer, TypeReference> argument : argumentTypes.entrySet()) {
            final TypeReference parameterType = candidate.getParameters().get(argument.getKey()).getParameterType();

            if (parameterType.isGenericParameter() && !argument.getValue().isPrimitive()) {
                inferred.putIfAbsent(parameterType.getSimpleName(), argument.getValue());
            }
        }

        // Nor does anything else than a lower bound for a variable which only bounds wildcards.
        for (final Map.Entry<GenericParameter, TypeReference> wildcard : wildcards) {
            if (!wildcard.getValue().isWildcardType()) {
                inferred.putIfAbsent(wildcard.getKey().getSimpleName(), wildcard.getValue());
            }
        }

        if (!satisfiesBounds(candidate.resolve(), inferred, bindings)) {
            return false;
        }

        // A wildcard only constrains the type for which its variable stands.
        for (final Map.Entry<GenericParameter, TypeReference> wildcard : wildcards) {
            final TypeReference bound = bindings.get(wildcard.getKey().getSimpleName());

            final TypeReference source = wildcard.getValue();

            if (bound == null) {
                // Without an exact type, every lower bound must still be within every upper one.
                if (!source.isWildcardType() && !isWithinUpperBounds(source, wildcard.getKey(), wildcards)) {
                    return false;
                }

                continue;
            }

            if (source.isWildcardType() && source.hasSuperBound()) {
                // Of "? super N", the argument must be a supertype of the type for which N stands.
                final TypeReference argument = source.getSuperBound();

                if (!argument.isWildcardType() && !MetadataHelper.isAssignableFrom(argument, bound)) {
                    return false;
                }
            }
            else {
                // Of "? extends N", the argument must be a subtype of it.
                final TypeReference argument = !source.isWildcardType() ? source
                                                                        : source.hasExtendsBound() ? source.getExtendsBound()
                                                                                                   : null;

                if (argument == null || !MetadataHelper.isAssignableFrom(bound, argument)) {
                    return false;
                }
            }
        }

        return all(
            argumentTypes.entrySet(),
            argument -> {
                final TypeReference parameterType = candidate.getParameters().get(argument.getKey()).getParameterType();
                final TypeReference bound = parameterType.isGenericParameter()
                                            ? bindings.get(parameterType.getSimpleName())
                                            : null;

                // An argument of the type variable itself must also be of the type for which it stands.
                return bound == null || accepts(bound, argument.getValue());
            }
        );
    }

    /**
     * Tells whether the cast of another argument, which does not change what the method throws, already
     * rules an overload out.
     */
    private static boolean isExcludedByAnotherCast(
        final MethodReference candidate,
        final MethodDefinition method,
        final int position) {

        final List<ParameterDefinition> parameters = method.getParameters();

        for (int i = 0, n = parameters.size(); i < n; i++) {
            final TypeReference declared = parameters.get(i).getParameterType();

            if (i != position &&
                declared.isGenericParameter() &&
                !isThrown((GenericParameter) declared, method) &&
                !accepts(candidate.getParameters().get(i).getParameterType(), MetadataHelper.erase(declared))) {

                return true;
            }
        }

        return false;
    }

    /** Tells whether a method takes the arguments without boxing or unboxing any, which comes first. */
    private static boolean isStrict(final MethodReference method, final Map<Integer, TypeReference> argumentTypes) {
        return all(
            argumentTypes.entrySet(),
            argument -> method.getParameters().get(argument.getKey()).getParameterType().isPrimitive() ==
                        argument.getValue().isPrimitive()
        );
    }

    /**
     * Tells whether a protected method of another package may be invoked through the receiver, which
     * requires it to be of the type being decompiled, or of one enclosing it.
     */
    private boolean isAccessibleThrough(final MethodReference method, final TypeReference receiverType) {
        final MethodDefinition definition = method.resolve();

        if (receiverType == null ||
            definition == null ||
            definition.isStatic() ||
            !definition.isProtected() ||
            StringUtilities.equals(definition.getDeclaringType().getPackageName(),
                                   _context.getCurrentType().getPackageName())) {

            return true;
        }

        for (TypeDefinition type = _context.getCurrentType(); type != null; ) {
            if (MetadataHelper.isSubType(MetadataHelper.erase(receiverType), MetadataHelper.erase(type))) {
                return true;
            }

            final TypeReference declaringType = type.getDeclaringType();

            type = declaringType != null ? declaringType.resolve() : null;
        }

        return false;
    }

    /**
     * Tells whether an overload accepts whatever the invoked method accepts, in which case the compiler
     * prefers the latter without any cast.
     */
    private static boolean isLessSpecific(final MethodReference candidate, final MethodDefinition method) {
        final List<ParameterDefinition> parameters = method.getParameters();

        for (int i = 0, n = parameters.size(); i < n; i++) {
            if (!accepts(candidate.getParameters().get(i).getParameterType(),
                         MetadataHelper.erase(parameters.get(i).getParameterType()))) {

                return false;
            }
        }

        return true;
    }

    /**
     * Tells whether the types for which type variables stand are within their parameterized bounds,
     * which may depend on those very types.
     */
    private static boolean satisfiesBounds(
        final MethodDefinition method,
        final Map<String, TypeReference> bindings,
        final Map<String, TypeReference> exact) {

        if (method == null) {
            return true;
        }

        final Map<TypeReference, TypeReference> substitutions = new HashMap<>();

        for (final GenericParameter variable : method.getGenericParameters()) {
            final TypeReference binding = bindings.get(variable.getSimpleName());

            if (binding != null) {
                substitutions.put(variable, binding);
            }
        }

        for (final Map.Entry<TypeReference, TypeReference> substitution : substitutions.entrySet()) {
            final GenericParameter variable = (GenericParameter) substitution.getKey();

            if (!variable.hasExtendsBound()) {
                continue;
            }

            for (final TypeReference component : typesOf(variable.getExtendsBound())) {
                // A variable bounding this one only has a fixed type when it was bound exactly.
                final TypeReference other = component.isGenericParameter() ? exact.get(component.getSimpleName())
                                                                           : null;

                if (other != null && !accepts(other, substitution.getValue())) {
                    return false;
                }

                if (!(component instanceof IGenericInstance)) {
                    continue;
                }

                final TypeReference bound = TypeSubstitutionVisitor.instance().visit(component, substitutions);

                if (!bound.containsGenericParameters() && !accepts(bound, substitution.getValue())) {
                    return false;
                }
            }
        }

        return true;
    }

    /** Tells whether a lower bound of a type variable is a subtype of each of its upper bounds. */
    private static boolean isWithinUpperBounds(
        final TypeReference lower,
        final GenericParameter variable,
        final List<Map.Entry<GenericParameter, TypeReference>> wildcards) {

        return all(
            wildcards,
            wildcard -> {
                final TypeReference upper = wildcard.getValue();

                return !wildcard.getKey().getSimpleName().equals(variable.getSimpleName()) ||
                       !upper.isWildcardType() ||
                       !upper.hasSuperBound() ||
                       upper.getSuperBound().isWildcardType() ||
                       MetadataHelper.isAssignableFrom(upper.getSuperBound(), lower);
            }
        );
    }

    /** Returns the lower or upper bound of a wildcard, or {@code null} if it is unbounded. */
    private static TypeReference wildcardBound(final TypeReference wildcard) {
        return wildcard.hasSuperBound() ? wildcard.getSuperBound()
                                        : wildcard.hasExtendsBound() ? wildcard.getExtendsBound() : null;
    }

    /**
     * Records the types for which the type variables of a parameter stand, however deeply they are
     * nested, and tells whether they agree.
     */
    private static boolean bind(
        final IGenericInstance parameter,
        final IGenericInstance argument,
        final Map<String, TypeReference> bindings,
        final List<Map.Entry<GenericParameter, TypeReference>> wildcards) {

        final List<TypeReference> targets = parameter.getTypeArguments();
        final List<TypeReference> sources = argument.getTypeArguments();

        for (int i = 0, n = Math.min(targets.size(), sources.size()); i < n; i++) {
            final TypeReference target = targets.get(i);
            final TypeReference source = sources.get(i);

            if (target.isWildcardType()) {
                final TypeReference bound = target.hasSuperBound() ? target.getSuperBound()
                                                                   : target.hasExtendsBound() ? target.getExtendsBound()
                                                                                              : null;

                if (bound instanceof IGenericInstance) {
                    // The variables within a parameterized bound stand for single types.
                    final TypeReference passed = !source.isWildcardType() ? source : wildcardBound(source);
                    final TypeDefinition definition = bound.resolve();
                    final TypeReference asBound = passed != null && definition != null
                                                  ? MetadataHelper.asSuper(definition, passed)
                                                  : null;

                    if (asBound instanceof IGenericInstance &&
                        !bind((IGenericInstance) bound, (IGenericInstance) asBound, bindings, wildcards)) {

                        return false;
                    }
                }

                if (bound != null && bound.isGenericParameter()) {
                    // Whether the bound is a lower one is recorded with the variable and the argument.
                    wildcards.add(new AbstractMap.SimpleEntry<>(
                        (GenericParameter) bound,
                        target.hasSuperBound() ? com.strobel.assembler.metadata.WildcardType.makeSuper(source)
                                               : source
                    ));
                }

                continue;
            }

            if (source.isWildcardType()) {
                continue;
            }

            TypeReference element = target;
            TypeReference argumentElement = source;

            // The elements of an array given as a type argument are bound as exactly as the array.
            while (element.isArray() && argumentElement.isArray()) {
                element = element.getElementType();
                argumentElement = argumentElement.getElementType();
            }

            if (element.isGenericParameter() && !argumentElement.isPrimitive()) {
                final TypeReference bound = bindings.putIfAbsent(element.getSimpleName(), argumentElement);

                if (bound != null && !MetadataHelper.isSameType(bound, argumentElement)) {
                    return false;
                }
            }
            else if (element instanceof IGenericInstance &&
                     argumentElement instanceof IGenericInstance &&
                     !bind((IGenericInstance) element, (IGenericInstance) argumentElement, bindings, wildcards)) {

                return false;
            }
        }

        return true;
    }

    private static boolean accepts(final TypeReference parameterType, final TypeReference argumentType) {
        if (parameterType.isGenericParameter()) {
            final TypeReference boxed = argumentType.isPrimitive() ? MetadataHelper.getBoxedTypeOrSelf(argumentType)
                                                                   : argumentType;

            return all(
                typesOf(MetadataHelper.getUpperBound(parameterType)),
                bound -> bound.isGenericParameter() ||
                         (bound.containsGenericParameters()
                          ? MetadataHelper.isAssignableFrom(MetadataHelper.erase(bound), boxed)
                          : accepts(bound, boxed))
            );
        }

        if (parameterType.isPrimitive()) {
            final TypeReference unboxed = MetadataHelper.getUnderlyingPrimitiveTypeOrSelf(argumentType);

            return unboxed.isPrimitive() && MetadataHelper.isAssignableFrom(parameterType, unboxed);
        }

        final TypeReference reference = argumentType.isPrimitive() ? MetadataHelper.getBoxedTypeOrSelf(argumentType)
                                                                   : argumentType;

        if (!MetadataHelper.isAssignableFrom(MetadataHelper.erase(parameterType), reference)) {
            return false;
        }

        if (parameterType.isArray() && reference.isArray() && !parameterType.getElementType().isPrimitive()) {
            return accepts(parameterType.getElementType(), reference.getElementType());
        }

        if (!(parameterType instanceof IGenericInstance)) {
            return true;
        }

        // The type arguments of the argument must be among those of the parameter.
        final TypeDefinition definition = parameterType.resolve();
        final TypeReference asParameterType = definition != null ? MetadataHelper.asSuper(definition, reference) : null;

        return !(asParameterType instanceof IGenericInstance) ||
               containsTypeArguments((IGenericInstance) parameterType, (IGenericInstance) asParameterType);
    }

    /** Tells whether the type arguments of a parameter allow those of an argument to be passed to it. */
    private static boolean containsTypeArguments(final IGenericInstance parameter, final IGenericInstance argument) {
        final List<TypeReference> targets = parameter.getTypeArguments();
        final List<TypeReference> sources = argument.getTypeArguments();

        for (int i = 0, n = Math.min(targets.size(), sources.size()); i < n; i++) {
            final TypeReference target = targets.get(i);

            if (target.isWildcardType() && target.hasSuperBound() && target.getSuperBound().isGenericParameter() &&
                !isSupertypeOfSome(sources.get(i), (GenericParameter) target.getSuperBound())) {

                return false;
            }

            if (target.isWildcardType() && target.hasExtendsBound() && target.getExtendsBound().isGenericParameter()) {
                final TypeReference source = sources.get(i);
                final TypeReference upper = !source.isWildcardType() ? source
                                                                     : source.hasExtendsBound() ? source.getExtendsBound()
                                                                                                : null;

                if (upper == null || !accepts(target.getExtendsBound(), upper)) {
                    return false;
                }
            }

            if (target instanceof IGenericInstance && target.containsGenericParameters() &&
                !sources.get(i).isWildcardType()) {

                // A nested type must be of the same class, with type arguments it allows in turn.
                final TypeReference source = sources.get(i);

                if (!(source instanceof IGenericInstance) ||
                    !MetadataHelper.isSameType(MetadataHelper.erase(target), MetadataHelper.erase(source)) ||
                    !containsTypeArguments((IGenericInstance) target, (IGenericInstance) source)) {

                    return false;
                }
            }

            // Of a type variable only the bounds are checked; other types which depend on one are left
            // to the inference of the compiler.
            if (target.isGenericParameter() ? !sources.get(i).isWildcardType() && !accepts(target, sources.get(i))
                                            : !target.containsGenericParameters() && !contains(target, sources.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tells whether a type argument may be a supertype of some type within the bounds of a type
     * variable, as a lower-bounded wildcard requires.
     */
    private static boolean isSupertypeOfSome(final TypeReference source, final GenericParameter variable) {
        final TypeReference lower = !source.isWildcardType() ? source
                                                             : source.hasSuperBound() ? source.getSuperBound() : null;

        return lower != null &&
               all(
                   typesOf(MetadataHelper.getUpperBound(variable)),
                   bound -> bound.isGenericParameter() ||
                            MetadataHelper.isAssignableFrom(MetadataHelper.erase(bound), lower) ||
                            MetadataHelper.isAssignableFrom(lower, MetadataHelper.erase(bound))
               );
    }

    private static boolean contains(final TypeReference target, final TypeReference source) {
        if (!target.isWildcardType()) {
            return MetadataHelper.isSameType(target, source);
        }

        if (target.hasSuperBound()) {
            final TypeReference lower = source.isWildcardType() ? source.hasSuperBound() ? source.getSuperBound() : null
                                                                : source;

            return lower != null && MetadataHelper.isAssignableFrom(lower, target.getSuperBound());
        }

        if (!target.hasExtendsBound() || BuiltinTypes.Object.isEquivalentTo(target.getExtendsBound())) {
            return true;
        }

        final TypeReference upper = source.isWildcardType() ? source.hasExtendsBound() ? source.getExtendsBound() : null
                                                            : source;

        return upper != null && MetadataHelper.isAssignableFrom(target.getExtendsBound(), upper);
    }

    /** Tells whether the type given to a generic parameter is also one which the method throws. */
    private static boolean isThrown(final GenericParameter parameter, final MethodDefinition method) {
        TypeReference type = parameter;

        while (type.isGenericParameter()) {
            final String name = type.getSimpleName();

            if (any(method.getThrownTypes(), t -> t.isGenericParameter() && t.getSimpleName().equals(name))) {
                return true;
            }

            if (!((GenericParameter) type).hasExtendsBound()) {
                break;
            }

            type = ((GenericParameter) type).getExtendsBound();
        }

        return false;
    }

    /**
     * Returns the type variable in scope which bounds a generic parameter, as erasing it would not
     * satisfy the parameter, or else the erasure of its bound, as seen from the receiver.
     */
    private TypeReference selectingBound(
        final GenericParameter parameter,
        final MethodDefinition method,
        final TypeReference receiverType) {

        TypeReference bound = parameter;

        while (isForeignTypeVariable(bound) && ((GenericParameter) bound).hasExtendsBound()) {
            bound = ((GenericParameter) bound).getExtendsBound();
        }

        if (bound.isGenericParameter()) {
            final GenericParameter variable = (GenericParameter) bound;

            // The receiver may substitute another type for a variable which is also in scope here.
            final TypeReference argument = typeArgumentOf(variable, method.getDeclaringType(), receiverType);

            if (argument != null) {
                return argument.isGenericParameter() ? argument : eraseBound(argument);
            }

            if (isClassTypeVariableInScope(variable)) {
                return variable;
            }
        }

        return eraseBound(MetadataHelper.getUpperBound(parameter));
    }

    /**
     * Returns the type, nameable here, which the receiver substitutes for a type variable of the class
     * declaring the invoked method, or {@code null}.
     */
    private TypeReference typeArgumentOf(
        final GenericParameter variable,
        final TypeDefinition declaringType,
        final TypeReference receiverType) {

        final TypeReference asDeclaringType = receiverType != null ? MetadataHelper.asSuper(declaringType, receiverType)
                                                                   : null;

        if (!(asDeclaringType instanceof IGenericInstance)) {
            return null;
        }

        final List<TypeReference> arguments = ((IGenericInstance) asDeclaringType).getTypeArguments();
        final List<GenericParameter> variables = declaringType.getGenericParameters();

        for (int i = 0; i < variables.size() && i < arguments.size(); i++) {
            TypeReference argument = arguments.get(i);

            // Only the lower bound of a wildcard is known to be accepted by its capture.
            if (argument.isWildcardType() && argument.hasSuperBound()) {
                argument = argument.getSuperBound();
            }

            if (variables.get(i).getSimpleName().equals(variable.getSimpleName()) &&
                !argument.isWildcardType() &&
                (!argument.isGenericParameter() || isTypeVariableInScope((GenericParameter) argument))) {

                return argument;
            }
        }

        return null;
    }

    private static List<TypeReference> typesOf(final TypeReference bound) {
        if (!(bound instanceof ICompoundType)) {
            return Collections.singletonList(bound);
        }

        final ICompoundType intersection = (ICompoundType) bound;
        final List<TypeReference> types = new ArrayList<>(intersection.getInterfaces());

        if (intersection.getBaseType() != null) {
            types.add(intersection.getBaseType());
        }

        return types;
    }

    /**
     * Finds a superclass of the argument which can be named here instead of an inaccessible bound: it
     * must satisfy the bound and still be rejected by the other overloads.
     */
    private TypeReference accessibleSubstitute(
        final List<TypeReference> bounds,
        final TypeReference argumentType,
        final List<TypeReference> competing) {

        TypeDefinition type = argumentType.resolve();

        while (type != null) {
            final TypeDefinition candidate = type;

            if (isAccessibleType(candidate) &&
                all(bounds, bound -> MetadataHelper.isAssignableFrom(bound, candidate)) &&
                !any(competing, other -> MetadataHelper.isAssignableFrom(other, candidate))) {

                return candidate;
            }

            final TypeReference baseType = candidate.getBaseType();

            type = baseType != null ? baseType.resolve() : null;
        }

        return null;
    }

    private boolean isTypeVariableInScope(final GenericParameter variable) {
        final GenericParameter inScope = _context.getCurrentMethod().findTypeVariable(variable.getSimpleName());

        return isClassTypeVariableInScope(variable) ||
               inScope != null &&
               inScope.getOwner() instanceof IMethodSignature &&
               variable.getOwner() instanceof IMethodSignature;
    }

    private boolean isClassTypeVariableInScope(final GenericParameter variable) {
        final MethodDefinition currentMethod = _context.getCurrentMethod();
        final GenericParameter inScope = currentMethod.findTypeVariable(variable.getSimpleName());

        return !currentMethod.isStatic() &&
               inScope != null &&
               inScope.getOwner() instanceof TypeReference &&
               variable.getOwner() instanceof TypeReference &&
               ((TypeReference) inScope.getOwner()).isEquivalentTo((TypeReference) variable.getOwner());
    }

    /** Tells whether the compiler may select a method from the type being decompiled. */
    private boolean isAccessible(final MethodReference method) {
        final MethodDefinition definition = method.resolve();

        return definition == null || isAccessible(definition, definition.getDeclaringType());
    }

    /** Tells whether a type, along with the types enclosing it, can be named in the type being decompiled. */
    private boolean isAccessibleType(final TypeReference type) {
        for (TypeDefinition definition = type.resolve(); definition != null; ) {
            final TypeReference declaringType = definition.getDeclaringType();

            if (!isAccessible(definition, declaringType != null ? declaringType : definition)) {
                return false;
            }

            definition = declaringType != null ? declaringType.resolve() : null;
        }

        return true;
    }

    private boolean isAccessible(final IMemberDefinition member, final TypeReference declaringType) {
        if (member.isPublic()) {
            return true;
        }

        final TypeReference currentType = _context.getCurrentType();

        if (member.isPrivate()) {
            return outermostType(declaringType).isEquivalentTo(outermostType(currentType));
        }

        if (outermostType(declaringType).getPackageName().equals(outermostType(currentType).getPackageName())) {
            return true;
        }

        if (member.isProtected()) {
            for (TypeReference type = currentType; type != null; type = type.getDeclaringType()) {
                if (MetadataHelper.isSubType(type, declaringType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static TypeReference outermostType(final TypeReference type) {
        TypeReference outermost = type;

        while (outermost.getDeclaringType() != null) {
            outermost = outermost.getDeclaringType();
        }

        return outermost;
    }

    /**
     * Erases a bound, which may mention type variables which cannot be named at the call site.  All the
     * types of an intersection are kept, as the argument must satisfy each of them.
     */
    private static TypeReference eraseBound(final TypeReference bound) {
        if (!(bound instanceof ICompoundType)) {
            return MetadataHelper.erase(bound);
        }

        final ICompoundType intersection = (ICompoundType) bound;
        final List<TypeReference> interfaces = new ArrayList<>();

        for (final TypeReference type : intersection.getInterfaces()) {
            interfaces.add(MetadataHelper.erase(type));
        }

        final TypeReference baseType = intersection.getBaseType();

        return new CompoundTypeReference(
            baseType != null ? MetadataHelper.erase(baseType) : null,
            interfaces,
            intersection.getResolver()
        );
    }

    private static boolean isOverloaded(final MethodReference method) {
        return method == null ||
               method.getDeclaringType().resolve() == null ||
               overloads(method).size() > 1;
    }

    /**
     * Returns the methods, including the given one, which have its name and number of parameters.
     */
    private static List<MethodReference> overloads(final MethodReference method) {
        final List<MethodReference> candidates = MetadataHelper.findMethods(
            method.getDeclaringType(),
            MetadataFilters.matchName(method.getName())
        );

        final List<MethodReference> overloads = new ArrayList<>();
        final int parameterCount = method.getParameters().size();

        for (final MethodReference candidate : candidates) {
            if (candidate.getParameters().size() == parameterCount) {
                overloads.add(candidate);
            }
        }

        return overloads;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean isCastRequired(final TypeReference targetType, final TypeReference sourceType, final boolean exactMatch) {
        if (targetType == null || sourceType == null) {
            return false;
        }

        if (targetType.isPrimitive()) {
            return sourceType.getSimpleType() != targetType.getSimpleType();
        }

        if (exactMatch) {
            return !MetadataHelper.isSameType(targetType, sourceType, true);
        }

        return !MetadataHelper.isAssignableFrom(targetType, sourceType);
    }

    private static Expression inlineAssembly(final com.strobel.decompiler.ast.Expression byteCode, final List<Expression> arguments) {
        return new InlinedBytecodeExpression(byteCode.getOffset(), byteCode.getCode(), byteCode.getOperand()).invoke(arguments);
    }

    private final static class MethodVariables {
        final MethodDefinition method;
        final VariableInfo<MethodDefinition> methodVariables;
        final Map<DynamicCallSite, Lambda> lambdas;
        final @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") Map<Lambda, VariableInfo<Lambda>> lambdaVariables;

        private MethodVariables(final MethodDefinition method) {
            this.method = VerifyArgument.notNull(method, "method");
            this.methodVariables = new VariableInfo<>(method, method.getParameters());
            this.lambdas = new LinkedHashMap<>();

            this.lambdaVariables = new DefaultMap<>(
                new Function<Lambda, VariableInfo<Lambda>>() {
                    @Override
                    public VariableInfo<Lambda> apply(final Lambda lambda) {
                        return new VariableInfo<>(lambda, lambda.getMethod().getParameters());
                    }
                }
            );
        }
    }

    private final static class VariableInfo<T> {
        final T owner;
        final List<ParameterDefinition> parameters;
        final Set<Variable> methodParameters = new LinkedHashSet<>();
        final Set<Variable> localVariables = new LinkedHashSet<>();
        final Set<ParameterDefinition> unmatchedParameters;

        NameVariables nv;

        public VariableInfo(final T owner, final List<ParameterDefinition> parameters) {
            this.owner = VerifyArgument.notNull(owner, "owner");
            this.parameters = VerifyArgument.notNull(parameters, "parameters");
            this.unmatchedParameters = new LinkedHashSet<>(parameters);
        }
    }

    private void populateVariables(final MethodVariables mv, final Block body) {
        final VariableInfo<?> vi = mv.methodVariables;

        populateVariables0(vi, body);

        for (final Lambda lambda : body.getSelfAndChildrenRecursive(Lambda.class)) {
            if (mv.lambdas.put(lambda.getCallSite(), lambda) == null) {
                populateVariables0(mv.lambdaVariables.get(lambda), lambda.getBody());
            }
        }

        final List<String> contextReservedNames = _context.getReservedVariableNames();
        final List<String> oldReservedVariables = new ArrayList<>(contextReservedNames);
        final Set<String> reservedNames = new HashSet<>(oldReservedVariables);

        nameVariables(body, vi, reservedNames);

        for (final Lambda lambda : mv.lambdas.values()) {
            nameVariables(lambda.getBody(), mv.lambdaVariables.get(lambda), reservedNames);
        }

        contextReservedNames.clear();
        contextReservedNames.addAll(oldReservedVariables);
    }

    private <T> void populateVariables0(final VariableInfo<T> vi, final Block body) {
        final List<Node> nodes = body.getSelfAndChildrenRecursive(Predicates.negate(Predicates.<Node>instanceOf(Lambda.class)), true);

        for (final Node n : nodes) {
            if (n instanceof com.strobel.decompiler.ast.Expression) {
                final com.strobel.decompiler.ast.Expression e = (com.strobel.decompiler.ast.Expression) n;
                final Object operand = e.getOperand();

                if (operand instanceof Variable) {
                    final Variable variable = (Variable) operand;

                    if (variable.isParameter()) {
                        vi.methodParameters.add(variable);
                        vi.unmatchedParameters.remove(variable.getOriginalParameter());
                    }
                    else {
                        vi.localVariables.add(variable);
                    }
                }

            }
            else if (n instanceof CatchBlock) {
                final CatchBlock catchBlock = (CatchBlock) n;
                final Variable exceptionVariable = catchBlock.getExceptionVariable();

                if (exceptionVariable != null) {
                    vi.localVariables.add(exceptionVariable);
                }
            }
        }

        final List<Variable> orderedParameters = new ArrayList<>();

        for (final ParameterDefinition p : vi.unmatchedParameters) {
            final Variable v = new Variable();
            v.setName(p.getName());
            v.setOriginalParameter(p);
            v.setType(p.getParameterType());
            orderedParameters.add(v);
        }

        orderedParameters.addAll(vi.methodParameters);

        Collections.sort(
            orderedParameters,
            new Comparator<Variable>() {
                @Override
                @SuppressWarnings("ConstantConditions")
                public int compare(@NotNull final Variable p1, @NotNull final Variable p2) {
                    return Integer.compare(p1.getOriginalParameter().getSlot(), p2.getOriginalParameter().getSlot());
                }
            }
        );

        vi.methodParameters.clear();
        vi.methodParameters.addAll(orderedParameters);
    }

    private void nameVariables(final Block body, final VariableInfo<?> vi, final Set<String> reservedNames) {
        final List<String> contextReservedNames = _context.getReservedVariableNames();

        contextReservedNames.clear();
        contextReservedNames.addAll(reservedNames);

        vi.nv = NameVariables.assignNamesToVariables(_context, vi.methodParameters, vi.localVariables, body);

        for (final Variable variable : vi.localVariables) {
            final String n = variable.getName();

            if (n != null) {
                reservedNames.add(n);
            }
        }
        for (final Variable variable : vi.methodParameters) {
            final String n = variable.getName();

            if (n != null) {
                reservedNames.add(n);
            }
        }
    }
}

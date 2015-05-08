package com.carrotsearch.hppcrt.generator.parser;

import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.carrotsearch.hppcrt.generator.TemplateOptions;
import com.carrotsearch.hppcrt.generator.TemplateProcessor;
import com.carrotsearch.hppcrt.generator.Type;
import com.carrotsearch.hppcrt.generator.parser.Java7Parser.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

class SignatureReplacementVisitor extends Java7ParserBaseVisitor<List<Replacement>>
{
    private final static List<Replacement> NONE = Collections.emptyList();

    private final TemplateOptions templateOptions;
    private final SignatureProcessor processor;

    public SignatureReplacementVisitor(final TemplateOptions templateOptions, final SignatureProcessor processor) {

        this.templateOptions = templateOptions;
        this.processor = processor;

        TemplateProcessor.setLoggerlevel(Logger.getLogger(SignatureReplacementVisitor.class.getName()), this.templateOptions.verbose);
    }

    private static class TypeBound
    {
        private final String originalBound;
        private final Type targetType;

        public TypeBound(final Type targetType, final String originalBound) {
            this.targetType = targetType;
            this.originalBound = originalBound;
        }

        public Type templateBound() {
            Preconditions.checkNotNull(this.targetType, "Target not a template bound: " + this.originalBound);
            return this.targetType;
        }

        public boolean isTemplateType() {
            return this.targetType != null;
        }

        public String originalBound() {
            return this.originalBound;
        }

        public String getBoxedType() {
            return templateBound().getBoxedType();
        }
    } //end class TypeBound

    private TypeBound typeBoundOf(final TypeParameterContext c) {

        final String symbol = c.Identifier().toString();
        switch (symbol) {
        case "KType":
            return new TypeBound(this.templateOptions.getKType(), c.getText());
        case "VType":
            return new TypeBound(this.templateOptions.getVType(), c.getText());
        default:
            return new TypeBound(null, c.getText());
        }
    }

    private TypeBound typeBoundOf(final TypeArgumentContext c, final Deque<Type> wildcards) {

        if (c.getText().equals("?")) {
            return new TypeBound(wildcards.removeFirst(), c.getText());
        }

        final TypeBound t = typeBoundOf(c.type());

        if (t.isTemplateType()) {
            return new TypeBound(t.templateBound(), getSourceText(c));
        }

        return new TypeBound(null, getSourceText(c));
    }

    private String getSourceText(final ParserRuleContext c) {
        return this.processor.tokenStream.getText(c.getSourceInterval());
    }

    private TypeBound typeBoundOf(final TypeContext c) {

        if (c.primitiveType() != null) {
            return new TypeBound(null, c.getText());
        }

        final TypeBound t = typeBoundOf(c.classOrInterfaceType());

        if (t.isTemplateType()) {
            return new TypeBound(t.templateBound(), c.getText());
        }

        return new TypeBound(null, c.getText());
    }

    private TypeBound typeBoundOf(final ClassOrInterfaceTypeContext c) {

        Preconditions.checkArgument(c.identifierTypePair().size() == 1, "Unexpected typeBoundOf context: " + c.getText());

        for (final IdentifierTypePairContext p : c.identifierTypePair()) {

            switch (p.Identifier().getText()) {
            case "KType":
                return new TypeBound(this.templateOptions.getKType(), p.getText());
            case "VType":
                return new TypeBound(this.templateOptions.getVType(), p.getText());
            }
        }

        return new TypeBound(null, c.getText());
    }

    @Override
    public List<Replacement> visitClassDeclaration(final ClassDeclarationContext ctx) {

        final List<Replacement> result = new ArrayList<>(super.visitClassDeclaration(ctx));

        log(Level.FINEST, "visitClassDeclaration", "calling parent, result = "
                + Lists.newArrayList(result).toString());

        String className = ctx.Identifier().getText();

        log(Level.FINE, "visitClassDeclaration", "className = " + className);

        if (isTemplateIdentifier(className) || true) {

            final List<TypeBound> typeBounds = new ArrayList<>();

            if (ctx.typeParameters() != null) {

                for (final TypeParameterContext c : ctx.typeParameters().typeParameter()) {
                    typeBounds.add(typeBoundOf(c));
                }

                result.add(new Replacement(ctx.typeParameters().getText(), ctx.typeParameters(), toString(typeBounds)));

                log(Level.FINEST, "visitClassDeclaration",
                        "type parameters replacements = " + result.get(result.size() - 1));
            }

            //1) Existing generic identifiers
            if (className.contains("KType") && typeBounds.size() >= 1) {
                className = className.replace("KType", typeBounds.get(0).templateBound().getBoxedType());
            }

            if (className.contains("VType") && typeBounds.size() >= 2) {
                className = className.replace("VType", typeBounds.get(1).templateBound().getBoxedType());
            }

            //2) At that point, if className still contains KType/VType, that
            // means that we had empty generic identifiers, so do a simple replace of className with matching TemplateOptions
            //This allow managing of KTypeArrays-like classes behaving like java.util.Arrays.
            if (className.contains("KType") && this.templateOptions.hasKType()) {
                className = className.replace("KType", this.templateOptions.ktype.getBoxedType());
            }

            if (className.contains("VType") && this.templateOptions.hasVType()) {
                className = className.replace("VType", this.templateOptions.vtype.getBoxedType());
            }

            result.add(new Replacement(ctx.Identifier().getText(), ctx.Identifier(), className));

            log(Level.FINEST, "visitClassDeclaration",
                    " result className replacement = " + result.get(result.size() - 1));
        }

        return result;
    }

    @Override
    public List<Replacement> visitInterfaceDeclaration(final InterfaceDeclarationContext ctx) {

        List<Replacement> result = super.visitInterfaceDeclaration(ctx);

        log(Level.FINEST, "visitInterfaceDeclaration", "calling parent, result = "
                + Lists.newArrayList(result).toString());

        String className = ctx.Identifier().getText();

        log(Level.FINEST, "visitInterfaceDeclaration", "className = " + className);

        if (isTemplateIdentifier(className)) {
            final List<TypeBound> typeBounds = new ArrayList<>();

            for (final TypeParameterContext c : ctx.typeParameters().typeParameter()) {
                typeBounds.add(typeBoundOf(c));
            }

            final Replacement replaceGenericTypes = new Replacement(ctx.typeParameters().getText(), ctx.typeParameters(),
                    toString(typeBounds));

            log(Level.FINEST, "visitInterfaceDeclaration", "replaceGenericTypes = " + replaceGenericTypes);

            int typeBoundIndex = 0;

            if (className.contains("KType")) {
                className = className.replace("KType", typeBounds.get(typeBoundIndex++).templateBound().getBoxedType());
            }

            if (className.contains("VType")) {
                className = className.replace("VType", typeBounds.get(typeBoundIndex++).templateBound().getBoxedType());
            }

            final Replacement replaceIdentifier = new Replacement(ctx.Identifier().getText(), ctx.Identifier(), className);

            log(Level.FINEST, "visitInterfaceDeclaration", "replaceIdentifier = " + replaceIdentifier);

            result = new ArrayList<>(result);

            result.addAll(Arrays.asList(replaceIdentifier, replaceGenericTypes));
        }

        return result;
    }

    @Override
    public List<Replacement> visitConstructorDeclaration(final ConstructorDeclarationContext ctx) {

        final List<Replacement> results = processIdentifier(ctx.Identifier(), super.visitConstructorDeclaration(ctx));

        log(Level.FINEST, "visitConstructorDeclaration", "results = " + Lists.newArrayList(results).toString());

        return results;
    }

    @Override
    public List<Replacement> visitPrimary(final PrimaryContext ctx) {

        final TerminalNode identifier = ctx.Identifier();

        if (identifier != null && isTemplateIdentifier(identifier.getText())) {

            log(Level.FINEST, "visitPrimary", "identifier (template) text = " + identifier.getText());

            return processIdentifier(identifier, SignatureReplacementVisitor.NONE);
        }

        log(Level.FINEST, "visitPrimary", "identifier (NON template) text = " + ctx.Identifier());

        return super.visitPrimary(ctx);
    }

    @Override
    public List<Replacement> visitGenericMethodDeclaration(final GenericMethodDeclarationContext ctx) {

        final ArrayList<String> bounds = new ArrayList<>();

        for (final TypeParameterContext c : ctx.typeParameters().typeParameter()) {

            switch (c.Identifier().getText()) {
            case "KType":
                Preconditions.checkArgument(c.typeBound() == null, "Unexpected type bound on template type: " + c.getText());
                if (this.templateOptions.isKTypeGeneric()) {
                    bounds.add(c.getText());
                }
                break;

            case "VType":
                Preconditions.checkArgument(c.typeBound() == null, "Unexpected type bound on template type: " + c.getText());
                if (this.templateOptions.isVTypeGeneric()) {
                    bounds.add(c.getText());
                }
                break;

            default:
                final TypeBoundContext tbc = c.typeBound();
                if (tbc != null) {
                    Preconditions.checkArgument(tbc.type().size() == 1, "Expected exactly one type bound: " + c.getText());
                    final TypeContext tctx = tbc.type().get(0);
                    final Interval sourceInterval = tbc.getSourceInterval();

                    final StringWriter sw = this.processor.reconstruct(new StringWriter(), this.processor.tokenStream,
                            sourceInterval.a, sourceInterval.b, visitType(tctx), this.templateOptions);
                    bounds.add(c.Identifier() + " extends " + sw.toString());

                } else {
                    bounds.add(c.getText());
                }
                break;
            } //end switch
        }

        final List<Replacement> replacements = new ArrayList<>();

        if (bounds.isEmpty()) {
            replacements.add(new Replacement(ctx.typeParameters().getText(), ctx.typeParameters(), ""));
        } else {
            replacements.add(new Replacement(ctx.typeParameters().getText(), ctx.typeParameters(), "<" + join(", ", bounds) + ">"));
        }

        log(Level.FINEST, "visitGenericMethodDeclaration", "Adding parent....");

        replacements.addAll(super.visitMethodDeclaration(ctx.methodDeclaration()));

        log(Level.FINEST, "visitGenericMethodDeclaration", "replacements = "
                + Lists.newArrayList(replacements).toString());

        return replacements;
    }

    @Override
    public List<Replacement> visitMethodDeclaration(final MethodDeclarationContext ctx) {

        final List<Replacement> replacements = new ArrayList<>();

        if (ctx.type() != null) {
            replacements.addAll(visitType(ctx.type()));
        }
        replacements.addAll(processIdentifier(ctx.Identifier(), SignatureReplacementVisitor.NONE));

        log(Level.FINEST, "visitMethodDeclaration", "replacements after processIdentifier = "
                + Lists.newArrayList(replacements).toString());

        replacements.addAll(visitFormalParameters(ctx.formalParameters()));

        log(Level.FINEST, "visitMethodDeclaration", "replacements after visitFormalParameters = "
                + Lists.newArrayList(replacements).toString());

        if (ctx.qualifiedNameList() != null) {
            replacements.addAll(visitQualifiedNameList(ctx.qualifiedNameList()));
        }

        log(Level.FINEST, "visitMethodDeclaration", "replacements after visitQualifiedNameList = "
                + Lists.newArrayList(replacements).toString());

        replacements.addAll(visitMethodBody(ctx.methodBody()));

        log(Level.FINEST, "visitMethodDeclaration", "replacements after visitMethodBody = "
                + Lists.newArrayList(replacements).toString());

        return replacements;
    }

    @Override
    public List<Replacement> visitIdentifierTypeOrDiamondPair(final IdentifierTypeOrDiamondPairContext ctx) {

        if (ctx.typeArgumentsOrDiamond() == null) {
            return processIdentifier(ctx.Identifier(), SignatureReplacementVisitor.NONE);
        }

        final List<Replacement> replacements = new ArrayList<>();
        String identifier = ctx.Identifier().getText();

        log(Level.FINE, "visitIdentifierTypeOrDiamondPair", "identifier = " + identifier);

        if (ctx.typeArgumentsOrDiamond().getText().equals("<>")) {

            if (identifier.contains("KType") && this.templateOptions.isKTypePrimitive()
                    && (!identifier.contains("VType") || this.templateOptions.isVTypePrimitive())) {
                replacements.add(new Replacement(identifier, ctx.typeArgumentsOrDiamond(), ""));
            }

            log(Level.FINEST, "visitIdentifierTypeOrDiamondPair", "replacements after '<>' = "
                    + Lists.newArrayList(replacements).toString());

            return processIdentifier(ctx.Identifier(), replacements);
        }

        final List<TypeBound> typeBounds = new ArrayList<>();
        final TypeArgumentsContext typeArguments = ctx.typeArgumentsOrDiamond().typeArguments();

        final Deque<Type> wildcards = getWildcards();

        //enumerate typeArguments: they could be complex, (type within type...), so visit them recursively.
        for (final TypeArgumentContext c : typeArguments.typeArgument()) {

            //TODO: Added :
            replacements.addAll(super.visitTypeArgument(c));

            typeBounds.add(typeBoundOf(c, wildcards));
        }

        //TODO : Remove ?
        //replacements.add(new Replacement(typeArguments.getText(), typeArguments, toString(typeBounds)));

        log(Level.FINEST, "visitIdentifierTypeOrDiamondPair", "replacements after diamond pair = "
                + Lists.newArrayList(replacements).toString());

        int typeBoundIndex = 0;

        if (identifier.contains("KType")) {

            final TypeBound bb = typeBounds.get(typeBoundIndex++);
            if (bb.isTemplateType()) {
                identifier = identifier.replace("KType", bb.getBoxedType());
            } else {
                identifier = identifier.replace("KType", "Object");
            }
        }

        if (identifier.contains("VType")) {
            final TypeBound bb = typeBounds.get(typeBoundIndex++);
            if (bb.isTemplateType()) {
                identifier = identifier.replace("VType", bb.getBoxedType());
            } else {
                identifier = identifier.replace("VType", "Object");
            }
        }

        replacements.add(new Replacement(ctx.Identifier().getText(), ctx.Identifier(), identifier));

        log(Level.FINEST, "visitIdentifierTypeOrDiamondPair",
                "replacements after enclosing identifier final replacement = " + Lists.newArrayList(replacements).toString());

        return replacements;
    }

    @Override
    public List<Replacement> visitCreatedName(final CreatedNameContext ctx) {

        log(Level.FINEST, "visitCreatedName", "calling parent...");

        return super.visitCreatedName(ctx);
    }

    @Override
    public List<Replacement> visitIdentifierTypePair(final IdentifierTypePairContext ctx) {

        String identifier = ctx.Identifier().getText();

        log(Level.FINEST, "visitIdentifierTypePair", "identifier = " + identifier);

        if (isTemplateIdentifier(identifier)) {

            if (ctx.typeArguments() != null) {

                final List<Replacement> replacements = new ArrayList<>();
                final List<TypeBound> typeBounds = new ArrayList<>();

                final Deque<Type> wildcards = getWildcards();

                for (final TypeArgumentContext c : ctx.typeArguments().typeArgument()) {
                    typeBounds.add(typeBoundOf(c, wildcards));
                }
                replacements.add(new Replacement(ctx.typeArguments().getText(), ctx.typeArguments(), toString(typeBounds)));

                int typeBoundIndex = 0;
                if (identifier.contains("KType")) {
                    final TypeBound bb = typeBounds.get(typeBoundIndex++);
                    if (bb.isTemplateType()) {
                        identifier = identifier.replace("KType", bb.getBoxedType());
                    } else {
                        identifier = identifier.replace("KType", "Object");
                    }
                }
                if (identifier.contains("VType")) {
                    final TypeBound bb = typeBounds.get(typeBoundIndex++);
                    if (bb.isTemplateType()) {
                        identifier = identifier.replace("VType", bb.getBoxedType());
                    } else {
                        identifier = identifier.replace("VType", "Object");
                    }
                }
                replacements.add(new Replacement(ctx.Identifier().getText(), ctx.Identifier(), identifier));

                log(Level.FINEST, "visitIdentifierTypePair",
                        "non-null template replacements = " + Lists.newArrayList(replacements));

                return replacements;
            }

            return processIdentifier(ctx.Identifier(), SignatureReplacementVisitor.NONE);
        }

        return super.visitIdentifierTypePair(ctx);
    }

    @Override
    public List<Replacement> visitQualifiedName(final QualifiedNameContext ctx) {

        List<Replacement> replacements = SignatureReplacementVisitor.NONE;

        for (final TerminalNode identifier : ctx.Identifier()) {
            String symbol = identifier.getText();
            if (isTemplateIdentifier(symbol)) {
                if (symbol.contains("KType")) {
                    symbol = symbol.replace("KType", this.templateOptions.getKType().getBoxedType());
                }
                if (symbol.contains("VType")) {
                    symbol = symbol.replace("VType", this.templateOptions.getVType().getBoxedType());
                }

                if (replacements == SignatureReplacementVisitor.NONE) {
                    replacements = new ArrayList<>();
                }
                replacements.add(new Replacement(identifier.getText(), identifier, symbol));
            }

        }
        log(Level.FINEST, "visitQualifiedName", "replacements = " + Lists.newArrayList(replacements));

        return replacements;
    }

    @Override
    protected List<Replacement> defaultResult() {
        return SignatureReplacementVisitor.NONE;
    }

    @Override
    protected List<Replacement> aggregateResult(final List<Replacement> first, final List<Replacement> second) {

        if (second.size() == 0) {
            return first;
        }

        if (first.size() == 0) {
            return second;
        }

        // Treat partial results as immutable.
        final List<Replacement> result = new ArrayList<Replacement>();
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private ArrayDeque<Type> getWildcards() {

        final ArrayDeque<Type> deque = new ArrayDeque<>();

        if (this.templateOptions.hasKType()) {
            deque.addLast(this.templateOptions.getKType());
        }

        if (this.templateOptions.hasVType()) {
            deque.addLast(this.templateOptions.getVType());
        }

        return deque;
    }

    private List<Replacement> processIdentifier(final TerminalNode ctx, List<Replacement> replacements) {

        String identifier = ctx.getText();

        if (isTemplateIdentifier(identifier)) {

            replacements = new ArrayList<>(replacements);
            switch (identifier) {
            case "KType":
                identifier = this.templateOptions.isKTypePrimitive() ? this.templateOptions.getKType().getType() : "KType";
                break;
            case "VType":
                identifier = this.templateOptions.isVTypePrimitive() ? this.templateOptions.getVType().getType() : "VType";
                break;
            default:
                if (identifier.contains("KType")) {
                    identifier = identifier.replace("KType", this.templateOptions.getKType().getBoxedType());
                }
                if (identifier.contains("VType")) {
                    identifier = identifier.replace("VType", this.templateOptions.getVType().getBoxedType());
                }
                break;
            }
            replacements.add(new Replacement(ctx.getText(), ctx, identifier));
        } //end if isTemplateIdentifier

        log(Level.FINEST, "processIdentifier", "input replacements = " + Lists.newArrayList(replacements));

        return replacements;
    }

    private String toString(final List<TypeBound> typeBounds) {

        final List<String> parts = new ArrayList<>();

        for (final TypeBound tb : typeBounds) {

            if (tb.isTemplateType()) {
                if (!tb.templateBound().isGeneric()) {
                    continue;
                }
            }
            parts.add(tb.originalBound());
        }
        return parts.isEmpty() ? "" : "<" + join(", ", parts) + ">";
    }

    private String join(final String on, final Iterable<String> parts) {

        final StringBuilder out = new StringBuilder();
        boolean prependOn = false;
        for (final String part : parts) {
            if (prependOn) {
                out.append(on);
            } else {
                prependOn = true;
            }
            out.append(part);
        }
        return out.toString();
    }

    private boolean isTemplateIdentifier(final String symbol) {
        return symbol.contains("KType") || symbol.contains("VType");
    }

    /**
     * log shortcut
     * @param lvl
     * @param message
     */
    private void log(final Level lvl, final String methodName, final String message) {

        Logger.getLogger(SignatureReplacementVisitor.class.getName()).log(lvl, methodName + " - " + message);
    }

}

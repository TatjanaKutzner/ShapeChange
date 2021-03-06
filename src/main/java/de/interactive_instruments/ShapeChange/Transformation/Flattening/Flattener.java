/**
 * ShapeChange - processing application schemas for geographic information
 *
 * This file is part of ShapeChange. ShapeChange takes a ISO 19109
 * Application Schema from a UML model and translates it into a
 * GML Application Schema or other implementation representations.
 *
 * Additional information about the software can be found at
 * http://shapechange.net/
 *
 * (c) 2002-2012 interactive instruments GmbH, Bonn, Germany
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact:
 * interactive instruments GmbH
 * Trierer Strasse 70-72
 * 53115 Bonn
 * Germany
 */
package de.interactive_instruments.ShapeChange.Transformation.Flattening;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.cycle.DirectedSimpleCycles;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.alg.cycle.TiernanSimpleCycles;
import org.jgrapht.graph.ClassBasedEdgeFactory;
import org.jgrapht.graph.DirectedMultigraph;

import de.interactive_instruments.ShapeChange.Multiplicity;
import de.interactive_instruments.ShapeChange.Options;
import de.interactive_instruments.ShapeChange.ProcessMapEntry;
import de.interactive_instruments.ShapeChange.ProcessRuleSet;
import de.interactive_instruments.ShapeChange.ShapeChangeAbortException;
import de.interactive_instruments.ShapeChange.ShapeChangeResult;
import de.interactive_instruments.ShapeChange.ShapeChangeResult.MessageContext;
import de.interactive_instruments.ShapeChange.StructuredNumber;
import de.interactive_instruments.ShapeChange.TransformerConfiguration;
import de.interactive_instruments.ShapeChange.Type;
import de.interactive_instruments.ShapeChange.Model.AssociationInfo;
import de.interactive_instruments.ShapeChange.Model.ClassInfo;
import de.interactive_instruments.ShapeChange.Model.Constraint;
import de.interactive_instruments.ShapeChange.Model.Info;
import de.interactive_instruments.ShapeChange.Model.Model;
import de.interactive_instruments.ShapeChange.Model.PackageInfo;
import de.interactive_instruments.ShapeChange.Model.PropertyInfo;
import de.interactive_instruments.ShapeChange.Model.TaggedValues;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericAssociationInfo;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericClassInfo;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericTextConstraint;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericModel;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericModel.PropertyCopyDuplicatBehaviorIndicator;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericModel.PropertyCopyPositionIndicator;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericPackageInfo;
import de.interactive_instruments.ShapeChange.Model.Generic.GenericPropertyInfo;
import de.interactive_instruments.ShapeChange.Transformation.Transformer;

/**
 * Encapsulates the logic for flattening/simplifying complex constructs within
 * an application schema.
 *
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments
 *         <dot> de)
 *
 */
public class Flattener implements Transformer {

	/* ------------------------------------------- */
	/* --- configuration parameter identifiers --- */
	/* ------------------------------------------- */

	public static final String TRANSFORMER_NAMESPACE_SUFFIX_PARAMETER = "targetNamespaceSuffix";
	public static final String PARAM_REMOVE_TYPE = "removeType";

	// lower case alias for properties is kept for backward compatibility
	public static final String TRANSFORMER_LOWER_CASE_ALIAS_FOR_PROPERTIES = "lowerCaseAliasForProperties";
	public static final String TRANSFORMER_LOWER_CASE_CODE_FOR_PROPERTIES = "lowerCaseCodeForProperties";

	// alias for enumeration values is kept for backward compatibility
	public static final String TRANSFORMER_ALIAS_FOR_ENUMERATION_VALUES = "aliasForEnumerationValues";
	public static final String TRANSFORMER_CODE_FOR_ENUMERATION_VALUES = "codeForEnumerationValues";

	public static final String TRANSFORMER_ENFORCE_OPTIONALITY = "enforceOptionality";

	// alias is kept for backward compatibility
	public static final String TRANSFORMER_KEEP_ORIGINAL_NAME_AS_ALIAS = "keepOriginalNameAsAlias";
	public static final String TRANSFORMER_KEEP_ORIGINAL_NAME_AS_CODE = "keepOriginalNameAsCode";

	public static final String TRANSFORMER_SEPARATOR_FOR_PROPERTY_FROM_UNION = "separatorForPropertyFromUnion";
	public static final String TRANSFORMER_SEPARATOR_FOR_PROPERTY_FROM_NON_UNION = "separatorForPropertyFromNonUnion";
	public static final String TRANSFORMER_SEPARATOR_FOR_PROPERTY_INDEX_NUMBER = "separatorForPropertyIndexNumber";
	public static final String TRANSFORMER_SEPARATOR_FOR_GEOMETRY_TYPE_SUFFIX = "separatorForGeometryTypeSuffix";

	// alias is kept for backward compatibility
	public static final String TRANSFORMER_REMOVE_PROPERTY_NAME_AND_ALIAS_COMPONENT = "removePropertyNameAndAliasComponent";
	public static final String TRANSFORMER_REMOVE_PROPERTY_NAME_AND_CODE_COMPONENT = "removePropertyNameAndCodeComponent";

	public static final String TRANSFORMER_CODEBY_TAGGEDVALUE = "codeByTaggedValue";

	// Parameters for RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY
	/**
	 * Regular expression; if the name of an object type matches this
	 * expression, then each navigable property whose value type is a feature
	 * type will be removed from the object type.
	 * <p>
	 * Applies to
	 * {@value #RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY}. The
	 * parameter is required if this rule is in effect.
	 */
	public static final String PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX = "removeObjectToFeatureNavRegex";
	/**
	 * If this parameter is set to <code>true</code> then execution of
	 * {@value #RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY} will
	 * also remove navigable properties whose value type is an object type.
	 * <p>
	 * Default value is <code>false</code>.
	 */
	public static final String PARAM_INCLUDE_OBJECT_NAV = "includeObjectToObjectNavigability";

	// Parameters for RULE_TRF_CLS_FLATTEN_INHERITANCE
	public static final String PARAM_INHERITANCE_INCLUDE_REGEX = "flattenInheritanceIncludeRegex";

	// Parameters for RULE_TRF_PROP_FLATTEN_TYPES
	public static final String PARAM_FLATTEN_OBJECT_TYPES = "flattenObjectTypes";
	public static final String PARAM_FLATTEN_OBJECT_TYPES_INCLUDE_REGEX = "flattenObjectTypesIncludeRegex";
	public static final String PARAM_FLATTEN_DATATYPES_EXCLUDE_REGEX = "flattenDataTypesExcludeRegex";

	// Parameters for RULE_TRF_CLS_REPLACE_WITH_UNION_PROPERTIES
	/**
	 * Alias: none
	 * <p>
	 * Type: String (with Java compliant regular expression)
	 * <p>
	 * Default Value: none
	 * <p>
	 * Behavior: This parameter identifies the unions that shall NOT be
	 * flattened. The value of this parameter contains a (Java compliant)
	 * regular expression which, if it matches the name of a union, marks it to
	 * be excluded by the Flattener.
	 * <p>
	 * Applies to Rule(s): rule-trf-cls-replace-with-union-properties
	 */
	public static final String PARAM_REPLACE_UNION_EXCLUDE_REGEX = "replaceUnionExcludeRegex";

	/**
	 * If this parameter is set to <code>true</code>, then properties that
	 * originate from flattening a specific union will be tagged (with tag '
	 * {@link #UNION_SET_TAG_NAME}'). This allows identifying which properties
	 * belong to the union after it has been flattened - just by looking at the
	 * tagged values. Properties from a union that are copied into another union
	 * will not be tracked. Also, tracking information will be removed / not
	 * created if union options replace a property with max multiplicity > 1
	 * (because then the union semantics will become irrelevant, as that
	 * property can have values from more than one union option).
	 */
	public static final String PARAM_INCLUDE_UNION_IDENTIFIER_TV = "includeUnionIdentifierTaggedValue";
	/**
	 * If, during execution of {@value #RULE_TRF_PROP_FLATTEN_TYPES}, a union is
	 * flattened, then by default the minimum multiplicity of the flattened
	 * property is set to 0. However, if the replaced property has a maximum
	 * multiplicity of 1 and {@value #PARAM_INCLUDE_UNION_IDENTIFIER_TV} is set
	 * to true, then the union semantics can be represented in the model. In
	 * that case, setting the minimum multiplicity of the flattened property to
	 * 0 would unnecessarily reduce valuable information. To prevent this from
	 * happening, set this parameter to <code>false</code> (the default is
	 * <code>true</code>).
	 */
	public static final String PARAM_SET_MIN_CARDINALITY_TO_ZERO_WHEN_MERGING_UNION = "setMinCardinalityToZeroWhenMergingUnion";
	/**
	 * If set to <code>true</code>, then descriptors of properties will be
	 * merged during type flattening.
	 */
	public static final String PARAM_MERGE_DESCRIPTORS = "mergeDescriptors";

	/**
	 * If this parameter is set to true, then type flattening is not performed
	 * for a (navigable) property that has its inClass (or one of its
	 * supertypes, up to the highest level) as value type.
	 */
	public static final String PARAM_IGNORE_REFLEXIVE_RELATIONSHIP_IN_TYPE_FLATTENING = "ignoreReflexiveRelationshipInTypeFlattening";

	// Parameters for RULE_TRF_PROP_FLATTEN_MULTIPLICITY
	public static final String PARAM_MAXOCCURS = "maxOccurs";
	public static final String PARAM_MAXOCCURS_FOR_SPECIFIC_PROPERTIES = "maxOccursForSpecificProperties";
	public static final String PARAM_IGNORE_FEATURE_OR_OBJECT_TYPED_PROPERTIES = "ignoreFeatureOrObjectTypedProperties";
	public static final String PARAM_IGNORE_FEATURE_TYPED_PROPERTIES = "ignoreFeatureTypedProperties";
	/**
	 * Integer > 1
	 */
	public static final String PARAM_MAX_MULTIPLICITY_THRESHOLD = "maxMultiplicityThreshold";

	// Parameters for RULE_TRF_PROP_FLATTEN_HOMOGENEOUSGEOMETRIES
	public static final String PARAM_HOMOGENEOUSGEOMETRIES_APPLY_ON_SUBTYPES = "applyHomogeneousGeometriesOnSubtypes";
	public static final String PARAM_HOMOGENEOUSGEOMETRIES_OMIT_RULE_FOR_CASE_OF_SINGLE_GEOMETRY_PROP = "omitHomogeneousGeometriesForTypesWithSingleGeometryProperty";

	// Parameters for RULE_TRF_CLS_REMOVE_INHERITANCE_RELATIONSHIP
	public static final String PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX = "removeInheritanceIncludeRegex";

	// =============================
	/* Flattener rule identifiers */
	// =============================
	public static final String RULE_TRF_ALL_FLATTEN_CODELISTS = "rule-trf-prop-flatten-codelists";
	public static final String RULE_TRF_ALL_FLATTEN_CONSTRAINTS = "rule-trf-all-flatten-constraints";
	public static final String RULE_TRF_ALL_FLATTEN_NAME = "rule-trf-all-flatten-name";
	public static final String RULE_TRF_ALL_REMOVETYPE = "rule-trf-all-removeType";

	/**
	 * Removes all navigable properties from a feature type if the value type of
	 * the property also is a feature type.
	 */
	public static final String RULE_TRF_ALL_REMOVE_FEATURETYPE_RELATIONSHIPS = "rule-trf-all-removeFeatureTypeRelationships";
	public static final String RULE_TRF_CLS_FLATTEN_INHERITANCE = "rule-trf-cls-flatten-inheritance";
	public static final String RULE_TRF_CLS_FLATTEN_INHERITANCE_ADD_ATTRIBUTES_AT_BOTTOM = "rule-trf-cls-flatten-inheritance-add-attributes-at-bottom";
	public static final String RULE_TRF_PROP_FLATTEN_HOMOGENEOUSGEOMETRIES = "rule-trf-prop-flatten-homogeneousgeometries";
	public static final String RULE_TRF_PROP_FLATTEN_MULTIPLICITY = "rule-trf-prop-flatten-multiplicity";
	public static final String RULE_TRF_PROP_FLATTEN_MULTIPLICITY_WITHMAXMULTTHRESHOLD = "rule-trf-prop-flatten-multiplicity-withMaxMultiplicityThreshold";
	public static final String RULE_TRF_PROP_FLATTEN_MULTIPLICITY_KEEPBIDIRECTIONALASSOCIATIONS = "rule-trf-prop-flatten-multiplicity-keepBiDirectionalAssociations";
	public static final String RULE_TRF_PROP_FLATTEN_ONINAS = "rule-trf-prop-flatten-ONINAs";
	public static final String RULE_TRF_PROP_FLATTEN_TYPES = "rule-trf-prop-flatten-types";
	public static final String RULE_TRF_PROP_OPTIONALITY = "rule-trf-prop-optionality";

	/**
	 * If the name of an object type matches the regular expression given via
	 * the configuration parameter
	 * {@value #PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX}, then each navigable
	 * property whose value type is a feature type will be removed from the
	 * object type. If the property is an attribute it will be removed from the
	 * model. If the property is an association role and the whole association
	 * is no longer navigable then the association will be removed.
	 */
	public static final String RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY = "rule-trf-prop-removeObjectToFeatureTypeNavigability";

	/**
	 * If a navigable association role has isFlatTarget tagged value set to true
	 * then it will be removed from the model.
	 * <p>
	 * This will ensure that the contents of the class (A) that owns the
	 * property (so the class on the other end of the association) can be copied
	 * into the value type (B) of the property but not the other way round - if
	 * (A) was flattened by {@value #RULE_TRF_PROP_FLATTEN_TYPES}. Setting the
	 * isFlatTarget tagged value is especially useful for managing how complex
	 * type flattening is applied in case of bi-directional association.
	 * <p>
	 * NOTE: if the isFlatTarget setting(s) on the association leads to the
	 * removal of the whole association (because both ends have been removed /
	 * are no longer navigable) a warning will be logged.
	 */
	public static final String RULE_TRF_PROP_REMOVE_NAVIGABILITY_BASEDON_ISFLATTARGET = "rule-trf-prop-removeNavigabilityBasedOnIsFlatTarget";

	// alias is kept for backward compatibility
	public static final String RULE_TRF_PROP_REMOVE_NAME_AND_ALIAS_COMPONENT = "rule-trf-prop-remove-name-and-alias-component";
	public static final String RULE_TRF_PROP_REMOVE_NAME_AND_CODE_COMPONENT = "rule-trf-prop-remove-name-and-code-component";

	public static final String RULE_TRF_PROP_UNION_DIRECT_OPTIONALITY = "rule-trf-prop-union-direct-optionality";

	/**
	 * Copies the attributes of a mixin to its subtypes and removes the mixin
	 * from the model.
	 * <p>
	 * Does NOT copy associations!
	 */
	public static final String RULE_TRF_CLS_DISSOLVE_MIXINS = "rule-trf-cls-dissolve-mixins";

	/**
	 * Removes inheritance relationships of classes to the classes whose name
	 * matches the regular expression provided by parameter
	 * {@value #PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX}. NOTE: Applies to
	 * classes in the whole model!
	 * 
	 */
	public static final String RULE_TRF_CLS_REMOVE_INHERITANCE_RELATIONSHIP = "rule-trf-cls-remove-inheritance-relationship";

	/**
	 * If only a single property (A) of a non-union type (e.g. a datatype) has a
	 * specific union as value type, and if that property has maximum
	 * multiplicity 1, then copies of the union properties replace property A.
	 * The sequenceNumbers of the property copies will be adjusted, so that the
	 * union property copies are correctly positioned within their new class.
	 * Their multiplicity is also adjusted: minimum occurrence is the product of
	 * the minimum occurrence of property A and the original union property,
	 * while the maximum occurrence is "*" if the maximum occurrence of one of
	 * the two properties is "*", otherwise it is the product of the maximum
	 * occurrences.
	 * 
	 * Finally, those unions that 1) have been processed by this rule and 2) are
	 * no longer used by properties of the selected schemas are removed from the
	 * model.
	 */
	public static final String RULE_TRF_CLS_REPLACE_WITH_UNION_PROPERTIES = "rule-trf-cls-replace-with-union-properties";

	// =============================
	/* Flattener requirements */
	// =============================
	/**
	 * If this requirement is included in the encoding rules together with
	 * {@value #RULE_TRF_PROP_FLATTEN_TYPES} then the Flattener will, before
	 * applying the rule, create a graph of selected schema types. Types that
	 * are not flattened will be excluded from the graph, i.e. especially
	 * feature types and - if they are not flattened - object types. Edges
	 * within the graph represent navigable routes (each edge from type A to
	 * type B contains the set of properties in type A that have B as value
	 * type). When creating edges, the flattener does not take into account
	 * additional information that might override navigability between types,
	 * like the tagged value {@value #TAGGED_VALUE_IS_FLAT_TARGET}. Such
	 * information should already have been used to remove non-navigable
	 * properties from the model before circular dependencies are being checked.
	 * The flattener will then identify all circles that may exist in the graph,
	 * logging them on log level INFO.
	 */
	public static final String REQ_FLATTEN_TYPES_IDENTIFY_CIRCULAR_DEPENDENCIES = "req-flattener-flattenTypes-identify-circular-dependencies";

	public static final String CHARACTER_STRING_CLASS_ID = "CharacterString_Class";
	/**
	 * Name of the tagged value that provides further information on type
	 * flattening direction for an association between feature/object and object
	 * types.
	 *
	 * If an end (i.e. a property) of an association has isFlatTarget tagged
	 * value = true then its type is going to be flattened (i.e. properties of
	 * the value type are flattened to the inClass of the property). The other
	 * association end will set to be non navigable, which is the same as
	 * removing the according property from the model (trying to keep the
	 * association). If the tagged value is set to true on both ends the
	 * association will be removed.
	 *
	 * Applies to rule {@value #RULE_TRF_PROP_FLATTEN_TYPES}
	 */
	public static final String TAGGED_VALUE_IS_FLAT_TARGET = "isFlatTarget";

	public static final String UNION_SET_TAG_NAME = "SC_UNION_SET";
	// =============================
	/* internal */
	// =============================
	/**
	 * This string is used as type id(entifier) whenever a type cannot be found
	 * in the model. Such situations can occur, for example, if a type is really
	 * not contained in the model (maybe it has been removed), or if the package
	 * that contains the type has explicitly been excluded while loading the
	 * input model.
	 */
	private static final String UNKNOWN = "UNKNOWN";

	private Options options = null;
	private ShapeChangeResult result = null;
	private Set<String> rules = null;

	/**
	 * Separator to use for name/code concatenation when flattening properties
	 * from a union type. The default value ("-") can be changed via the
	 * parameter {@value #TRANSFORMER_SEPARATOR_FOR_PROPERTY_FROM_UNION}. A
	 * separator of length 0 is not allowed.
	 */
	private String separatorForPropertyFromUnion = "-";
	/**
	 * Separator to use for name/code concatenation when flattening properties
	 * from a NON union type. The default value (".") can be changed via the
	 * parameter {@value #TRANSFORMER_SEPARATOR_FOR_PROPERTY_FROM_NON_UNION}. A
	 * separator of length 0 is not allowed.
	 */
	private String separatorForPropertyFromNonUnion = ".";
	/**
	 * Separator to use for concatenating the name/code of a property with an
	 * index number while flattening multiplicity. The default value ("_") can
	 * be changed via the parameter
	 * {@value #TRANSFORMER_SEPARATOR_FOR_PROPERTY_INDEX_NUMBER}. A separator of
	 * length 0 is allowed.
	 */
	private String separatorForPropertyIndexNumber = "_";

	/**
	 * Defines the behavior for managing code values of model elements. If this
	 * is <code>null</code> then the alias is used to look up and store the code
	 * value of a model element. Otherwise this provides the name of the tagged
	 * value via which the code value is managed. The value of this property is
	 * governed by a processing parameter that can be set for the configuration
	 * (parameter codeByTaggedValue).
	 */
	private String tvNameForCodeValue = null;

	private boolean flattenObjectTypes = true;
	private boolean ignoreReflexiveRelationshipInTypeFlattening = false;

	private String includeObjectTypeRegex = null;
	private Pattern includeObjectTypePattern = null;
	private String excludeDataTypeRegex = null;
	private Pattern excludeDataTypePattern = null;

	public Flattener() {
		// nothing special to do here
	}

	public void process(GenericModel genModel, Options options,
			TransformerConfiguration trfConfig, ShapeChangeResult result)
			throws ShapeChangeAbortException {

		this.options = options;
		this.result = result;

		Map<String, ProcessRuleSet> ruleSets = trfConfig.getRuleSets();

		// for now we simply get the set of all rules defined for the
		// transformation
		rules = new HashSet<String>();
		if (!ruleSets.isEmpty()) {
			for (ProcessRuleSet ruleSet : ruleSets.values()) {
				if (ruleSet.getAdditionalRules() != null) {
					rules.addAll(ruleSet.getAdditionalRules());
				}
			}
		}

		// because there are no mandatory - in other words default - rules for a
		// Flattener, simply return the model if no rules are defined in the
		// rule sets (which the schema allows)
		if (rules.isEmpty())
			return;

		result.addInfo(null, 20317, "processing");

		// apply overall processing defined for transformer

		// determine if a namespace suffix shall be added
		if (trfConfig.hasParameter(TRANSFORMER_NAMESPACE_SUFFIX_PARAMETER)) {
			String targetNamespaceSuffix = trfConfig
					.getParameterValue(TRANSFORMER_NAMESPACE_SUFFIX_PARAMETER);

			if (targetNamespaceSuffix == null
					|| targetNamespaceSuffix.trim().length() == 0) {
				// nothing to do
			} else {

				SortedSet<GenericPackageInfo> appSchemas = genModel
						.selectedSchemas();

				if (appSchemas != null) {

					for (GenericPackageInfo appSchema : appSchemas) {

						String targetNamespace = appSchema.targetNamespace();

						if (targetNamespace.endsWith(targetNamespaceSuffix))
							continue;

						for (GenericPackageInfo pi : appSchema.getAllPackages(
								new HashSet<GenericPackageInfo>())) {
							/*
							 * TBD: some more processing to identify if a '/' is
							 * missing or too much
							 */

							// just in case a package in the tree is not in the
							// app schema namespace
							if (pi.targetNamespace().equals(targetNamespace)) {
								pi.setTargetNamespace(targetNamespace
										+ targetNamespaceSuffix);
							}
						}
					}
				}
			}
		}

		// ====================================
		/*
		 * Determine parameter values that are relevant for multiple rules.
		 */
		// ====================================

		/*
		 * Determine the behavior for code value management: shall the model
		 * element alias be used to look up and store its code value, or a
		 * specific tagged value?
		 */

		if (trfConfig.hasParameter(TRANSFORMER_CODEBY_TAGGEDVALUE)) {

			tvNameForCodeValue = trfConfig
					.getParameterValue(TRANSFORMER_CODEBY_TAGGEDVALUE);

			if (tvNameForCodeValue == null
					|| tvNameForCodeValue.trim().length() == 0) {
				result.addError(null, 20309, TRANSFORMER_CODEBY_TAGGEDVALUE);
				return;
			}
		}

		String separatorForPropertyFromUnionParam = trfConfig.getParameterValue(
				TRANSFORMER_SEPARATOR_FOR_PROPERTY_FROM_UNION);
		if (separatorForPropertyFromUnionParam != null
				&& separatorForPropertyFromUnionParam.trim().length() > 0) {

			// Note that a length of 0 is not allowed because we want to be able
			// to identify the separator
			separatorForPropertyFromUnion = separatorForPropertyFromUnionParam;
		}

		String separatorForPropertyFromNonUnionParam = trfConfig
				.getParameterValue(
						TRANSFORMER_SEPARATOR_FOR_PROPERTY_FROM_NON_UNION);
		if (separatorForPropertyFromNonUnionParam != null
				&& separatorForPropertyFromNonUnionParam.trim().length() > 0) {

			// Note that a length of 0 is not allowed because we want to be able
			// to identify the separator
			separatorForPropertyFromNonUnion = separatorForPropertyFromNonUnionParam;
		}

		if (separatorForPropertyFromUnion
				.equals(separatorForPropertyFromNonUnion)) {
			// For now we allow the separators to be equal; this could change in
			// the future.
		}

		if (trfConfig.hasParameter(PARAM_FLATTEN_OBJECT_TYPES)) {

			String tmp = trfConfig
					.getParameterValue(PARAM_FLATTEN_OBJECT_TYPES);

			if (tmp != null && tmp.trim().equalsIgnoreCase("false")) {

				flattenObjectTypes = false;
			}
		}

		if (trfConfig.hasParameter(
				PARAM_IGNORE_REFLEXIVE_RELATIONSHIP_IN_TYPE_FLATTENING)) {

			String tmp = trfConfig.getParameterValue(
					PARAM_IGNORE_REFLEXIVE_RELATIONSHIP_IN_TYPE_FLATTENING);

			if (tmp != null && tmp.trim().equalsIgnoreCase("true")) {

				ignoreReflexiveRelationshipInTypeFlattening = true;
			}
		}

		if (trfConfig.hasParameter(PARAM_FLATTEN_OBJECT_TYPES_INCLUDE_REGEX)) {
			includeObjectTypeRegex = trfConfig.getParameterValue(
					PARAM_FLATTEN_OBJECT_TYPES_INCLUDE_REGEX);
			includeObjectTypePattern = Pattern.compile(includeObjectTypeRegex);
		}

		if (trfConfig.hasParameter(PARAM_FLATTEN_DATATYPES_EXCLUDE_REGEX)) {
			excludeDataTypeRegex = trfConfig
					.getParameterValue(PARAM_FLATTEN_DATATYPES_EXCLUDE_REGEX);
			excludeDataTypePattern = Pattern.compile(excludeDataTypeRegex);
		}

		// ====================================
		/*
		 * after overall processing has been performed, execute rules on generic
		 * model
		 */
		// ====================================

		if (rules.contains(RULE_TRF_ALL_REMOVETYPE)) {

			result.addInfo(null, 20103, RULE_TRF_ALL_REMOVETYPE);
			applyRuleRemoveType(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_CLS_DISSOLVE_MIXINS)) {

			result.addInfo(null, 20103, RULE_TRF_CLS_DISSOLVE_MIXINS);
			applyRuleDissolveMixins(genModel, trfConfig);
		}

		if (rules.contains(
				RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY)) {

			result.addInfo(null, 20103,
					RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY);

			applyRuleRemoveObjectToFeatureTypeNavigability(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_ALL_REMOVE_FEATURETYPE_RELATIONSHIPS)) {

			result.addInfo(null, 20103,
					RULE_TRF_ALL_REMOVE_FEATURETYPE_RELATIONSHIPS);
			applyRuleRemoveFeatureTypeRelationships(genModel, trfConfig);
		}

		if (rules.contains(
				RULE_TRF_PROP_REMOVE_NAVIGABILITY_BASEDON_ISFLATTARGET)) {

			result.addInfo(null, 20103,
					RULE_TRF_PROP_REMOVE_NAVIGABILITY_BASEDON_ISFLATTARGET);
			applyRuleRemoveNavigabilityBasedOnFlatTargetSetting(genModel,
					trfConfig);
		}

		// ====================================

		if (rules.contains(RULE_TRF_ALL_FLATTEN_CONSTRAINTS)) {
			result.addInfo(null, 20103, RULE_TRF_ALL_FLATTEN_CONSTRAINTS);
			applyRuleClsFlattenConstraints(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_ALL_FLATTEN_CODELISTS)) {
			result.addInfo(null, 20103, RULE_TRF_ALL_FLATTEN_CODELISTS);
			applyRuleFlattenCodeLists(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_PROP_FLATTEN_ONINAS)) {
			result.addInfo(null, 20103, RULE_TRF_PROP_FLATTEN_ONINAS);
			applyRuleONINAs(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_PROP_OPTIONALITY)) {
			result.addInfo(null, 20103, RULE_TRF_PROP_OPTIONALITY);
			applyRuleOptionality(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_CLS_FLATTEN_INHERITANCE)) {
			result.addInfo(null, 20103, RULE_TRF_CLS_FLATTEN_INHERITANCE);
			applyRuleInheritance(genModel, trfConfig);
		}

		/*
		 * Identify circular dependencies after inheritance has been flattened
		 * but before multiplicity is flattened (to avoid noise).
		 */
		if (rules.contains(REQ_FLATTEN_TYPES_IDENTIFY_CIRCULAR_DEPENDENCIES)) {

			identifyCircularDependencies(
					computeTypesToProcessForFlattenTypes(genModel, trfConfig));

			result.addInfo(REQ_FLATTEN_TYPES_IDENTIFY_CIRCULAR_DEPENDENCIES
					+ " completed.");
		}

		if (rules.contains(RULE_TRF_PROP_FLATTEN_MULTIPLICITY)) {
			result.addInfo(null, 20103, RULE_TRF_PROP_FLATTEN_MULTIPLICITY);
			applyRuleMultiplicity(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_CLS_REPLACE_WITH_UNION_PROPERTIES)) {
			result.addInfo(null, 20103,
					RULE_TRF_CLS_REPLACE_WITH_UNION_PROPERTIES);
			applyRuleUnionReplace(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_PROP_FLATTEN_TYPES)) {
			result.addInfo(null, 20103, RULE_TRF_PROP_FLATTEN_TYPES);
			applyRuleFlattenTypes(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_ALL_FLATTEN_NAME)) {
			result.addInfo(null, 20103, RULE_TRF_ALL_FLATTEN_NAME);
			applyRuleAllFlattenName(genModel, trfConfig);
		}

		// RULE_TRF_PROP_REMOVE_NAME_AND_CODE_COMPONENT for backward
		// compatibility
		if (rules.contains(RULE_TRF_PROP_REMOVE_NAME_AND_CODE_COMPONENT)
				|| rules.contains(
						RULE_TRF_PROP_REMOVE_NAME_AND_ALIAS_COMPONENT)) {
			result.addInfo(null, 20103,
					RULE_TRF_PROP_REMOVE_NAME_AND_CODE_COMPONENT + "/"
							+ RULE_TRF_PROP_REMOVE_NAME_AND_ALIAS_COMPONENT);
			applyRulePropFlattenRemoveNameAndCodeComponent(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_PROP_FLATTEN_HOMOGENEOUSGEOMETRIES)) {
			result.addInfo(null, 20103,
					RULE_TRF_PROP_FLATTEN_HOMOGENEOUSGEOMETRIES);
			applyRulePropFlattenHomogeneousGeometries(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_PROP_UNION_DIRECT_OPTIONALITY)) {
			result.addInfo(null, 20103, RULE_TRF_PROP_UNION_DIRECT_OPTIONALITY);
			applyRulePropUnionDirectOptionality(genModel, trfConfig);
		}

		if (rules.contains(RULE_TRF_CLS_REMOVE_INHERITANCE_RELATIONSHIP)) {
			result.addInfo(null, 20103,
					RULE_TRF_CLS_REMOVE_INHERITANCE_RELATIONSHIP);
			applyRuleRemoveInheritanceRelationship(genModel, trfConfig);
		}

		// postprocessing
		result.addInfo(null, 20317, "postprocessing");

		if (rules.contains(RULE_TRF_ALL_FLATTEN_CONSTRAINTS)) {

			// ensure that there are no duplicate text constraints

			for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

				if (genCi.hasConstraints()) {

					List<Constraint> classConstraints = genCi.constraints();
					Vector<Constraint> newConstraints = new Vector<Constraint>(
							classConstraints.size());

					SortedSet<String> constraintTexts = new TreeSet<String>();

					for (Constraint con : classConstraints) {

						if (!constraintTexts.contains(con.text())) {
							newConstraints.add(con);
						}
					}

					genCi.setConstraints(newConstraints);
				}

				// create copies of constraints in all app schema properties
				for (PropertyInfo pi : genCi.properties().values()) {

					GenericPropertyInfo genPi = (GenericPropertyInfo) pi;

					if (genPi.hasConstraints()) {

						List<Constraint> propConstraints = genPi.constraints();
						List<Constraint> newConstraints = new Vector<Constraint>(
								propConstraints.size());

						SortedSet<String> constraintTexts = new TreeSet<String>();

						for (Constraint con : propConstraints) {

							if (!constraintTexts.contains(con.text())) {
								newConstraints.add(con);
							}
						}

						genPi.setConstraints(newConstraints);
					}
				}
			}
		}
	}

	/**
	 * @see #RULE_TRF_CLS_DISSOLVE_MIXINS
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleDissolveMixins(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		Set<GenericClassInfo> mixinsToRemove = new HashSet<GenericClassInfo>();

		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			if (genCi.category() == Options.MIXIN) {

				this.copyContentToSubtypes(genModel, genCi);
				mixinsToRemove.add(genCi);
			}
		}

		for (GenericClassInfo mixin : mixinsToRemove) {

			genModel.remove(mixin);
		}
	}

	/**
	 * @see #RULE_TRF_CLS_REMOVE_INHERITANCE_RELATIONSHIP
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleRemoveInheritanceRelationship(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		if (!trfConfig.hasParameter(PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX)
				|| trfConfig
						.getParameterValue(
								PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX)
						.trim().isEmpty()) {
			result.addWarning(null, 20343,
					PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX,
					RULE_TRF_CLS_REMOVE_INHERITANCE_RELATIONSHIP);
			return;
		}

		String includeRegex = trfConfig
				.getParameterValue(PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX)
				.trim();

		/*
		 * identify the supertypes in the model that shall be disconnected from
		 * their subtypes
		 */
		Pattern includePattern = Pattern.compile(includeRegex);

		Set<String> idsOfRelevantSupertypes = new HashSet<String>();

		for (GenericClassInfo genCi : genModel.getGenClasses().values()) {

			Matcher m = includePattern.matcher(genCi.name());

			if (m.matches()) {
				idsOfRelevantSupertypes.add(genCi.id());

				genCi.setSubtypes(null);
				
				result.addDebug(null, 20344, genCi.name(), includeRegex, PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX);
			} else {
				result.addDebug(null, 20345, genCi.name(), includeRegex, PARAM_REMOVE_INHERITANCE_INCLUDE_REGEX);
			}
		}

		for (GenericClassInfo genCi : genModel.getGenClasses().values()) {

			if (genCi.baseClass() != null && idsOfRelevantSupertypes
					.contains(genCi.baseClass().id())) {
				genCi.setBaseClass(null);
			}

			TreeSet<String> idsOfSupertypesToKeep = new TreeSet<String>();

			for (String supertypeId : genCi.supertypes()) {
				if (idsOfRelevantSupertypes.contains(supertypeId)) {
					/*
					 * alright, we won't add this supertype to the set of
					 * supertypes to keep
					 */
				} else {
					idsOfSupertypesToKeep.add(supertypeId);
				}
			}

			genCi.setSupertypes(idsOfSupertypesToKeep);

			if (idsOfSupertypesToKeep.size() == 1) {
				GenericClassInfo base = genModel.getGenClasses()
						.get(idsOfSupertypesToKeep.first());
				genCi.setBaseClass(base);
			}
		}
	}

	private void applyRuleRemoveFeatureTypeRelationships(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		/*
		 * Identify association roles and attributes that represent a
		 * relationship between feature types
		 */
		Set<GenericPropertyInfo> relsToRemove = new HashSet<GenericPropertyInfo>();

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			if (genPi.inClass().category() == Options.FEATURE
					&& genPi.categoryOfValue() == Options.FEATURE) {

				relsToRemove.add(genPi);
			}
		}

		// remove identified relationships
		for (GenericPropertyInfo genPi : relsToRemove) {
			genModel.remove(genPi, false);
		}
	}

	private void applyRuleFlattenCodeLists(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		List<GenericClassInfo> codeListCisToRemove = new ArrayList<GenericClassInfo>();

		Map<String, GenericClassInfo> genCisById = genModel.getGenClasses();

		ClassInfo characterStringCi = genModel.classByName("CharacterString");
		String characterStringCiId;
		if (characterStringCi == null) {
			characterStringCiId = CHARACTER_STRING_CLASS_ID;
		} else {
			characterStringCiId = characterStringCi.id();
		}

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			Type genPiType = genPi.typeInfo();
			ClassInfo typeCi = genModel.classById(genPiType.id);

			if (typeCi != null && typeCi.category() == Options.CODELIST) {

				genPiType.id = characterStringCiId;
				genPiType.name = "CharacterString";

				if (genCisById.containsKey(typeCi.id())) {
					codeListCisToRemove.add(genCisById.get(typeCi.id()));
				}
			}
		}

		genModel.remove(codeListCisToRemove);
	}

	private void applyRulePropFlattenRemoveNameAndCodeComponent(
			GenericModel genModel, TransformerConfiguration trfConfig) {

		String[] propNameCodeComponentsToRemove = trfConfig
				.getListParameterValue(
						TRANSFORMER_REMOVE_PROPERTY_NAME_AND_CODE_COMPONENT);

		if (propNameCodeComponentsToRemove == null) {
			propNameCodeComponentsToRemove = trfConfig.getListParameterValue(
					TRANSFORMER_REMOVE_PROPERTY_NAME_AND_ALIAS_COMPONENT);
		}

		if (propNameCodeComponentsToRemove == null
				|| propNameCodeComponentsToRemove.length == 0)
			return;

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			for (String compToRemove : propNameCodeComponentsToRemove) {

				genPi.setName(genPi.name().replaceAll(compToRemove, ""));

				if (hasCode(genPi)) {
					String oldCode = getCode(genPi);
					String newCode = oldCode.replaceAll(compToRemove, "");
					setCode(genPi, newCode);
				}
			}
		}
	}

	private void applyRuleRemoveType(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		// First identify if the removeType parameter exists and if so, which -
		// if any - types shall be removed from the model.
		String[] typesToRemove = trfConfig
				.getListParameterValue(PARAM_REMOVE_TYPE);

		if (typesToRemove == null || typesToRemove.length == 0) {
			result.addWarning(null, 20324);
			return;
		}

		/*
		 * Now identify if the types exist in the application schema; if so,
		 * remove all properties and associations in the app schema that use
		 * these types, remove the types themselves, and also remove any direct
		 * inheritance relationships with these types.
		 */
		for (String typeToRemove : typesToRemove) {

			ClassInfo ciToRemove = genModel.classByName(typeToRemove);

			if (ciToRemove == null) {

				continue;

			} else {

				if (ciToRemove instanceof GenericClassInfo) {

					GenericClassInfo genCiToRemove = (GenericClassInfo) ciToRemove;

					genModel.remove(genCiToRemove);

				} else {

					result.addWarning(null, 20321, ciToRemove.name());
				}
			}
		}
	}

	private void applyRulePropFlattenHomogeneousGeometries(
			GenericModel genModel, TransformerConfiguration trfConfig) {

		ShapeChangeResult result = genModel.result();

		boolean applyOnSubtypes = false;

		if (trfConfig
				.hasParameter(PARAM_HOMOGENEOUSGEOMETRIES_APPLY_ON_SUBTYPES)) {
			String paramValue = trfConfig.getParameterValue(
					PARAM_HOMOGENEOUSGEOMETRIES_APPLY_ON_SUBTYPES);
			Boolean b = new Boolean(paramValue);

			result.addDebug(null, 20102,
					PARAM_HOMOGENEOUSGEOMETRIES_APPLY_ON_SUBTYPES,
					b.toString());
			applyOnSubtypes = b;
		}

		boolean omitHomogeneousGeometriesForTypesWithSingleGeometryProperty = false;
		if (trfConfig.hasParameter(
				PARAM_HOMOGENEOUSGEOMETRIES_OMIT_RULE_FOR_CASE_OF_SINGLE_GEOMETRY_PROP)) {
			String paramValue = trfConfig.getParameterValue(
					PARAM_HOMOGENEOUSGEOMETRIES_OMIT_RULE_FOR_CASE_OF_SINGLE_GEOMETRY_PROP);
			Boolean b = new Boolean(paramValue);

			result.addDebug(null, 20102,
					PARAM_HOMOGENEOUSGEOMETRIES_OMIT_RULE_FOR_CASE_OF_SINGLE_GEOMETRY_PROP,
					b.toString());
			omitHomogeneousGeometriesForTypesWithSingleGeometryProperty = b;
		}

		String separatorForGeometryTypeSuffix = "";
		String separatorForGeometryTypeSuffixParam = trfConfig
				.getParameterValue(
						TRANSFORMER_SEPARATOR_FOR_GEOMETRY_TYPE_SUFFIX);
		if (separatorForGeometryTypeSuffixParam != null) {
			separatorForGeometryTypeSuffix = separatorForGeometryTypeSuffixParam;
		}

		/*
		 * For each featureType FT that has properties with a geometry type
		 * (GM_Object, GM_Point, GM_Curve, GM_Surface, GM_MultiPoint etc.):
		 *
		 * 1. Create an information set S for all FT properties with the same
		 * geometry type. For each property that is a key in S, identify the
		 * list of associated FT properties via their name: for each key in S,
		 * if the name of the key has “.” in it, get the part of the name that
		 * ends with the rightmost “.” – all FT properties whose name starts
		 * with the same string belong to the list of associated properties.
		 * Note that this list can be empty if the key / geometry property name
		 * contains no “.”.
		 *
		 * 2. Remove all information sets that belong to one of the types from
		 * the type mapping that have no target types.
		 *
		 * 3. For each information set S that belongs to a property with a type
		 * for which a target type as well as parameter are given in the type
		 * mapping: create a copy of FT, remove all properties from the other
		 * information sets, set the type of the property represented by S to
		 * the target type, and append the value of the type mapping parameter
		 * to the name and alias of the copy.
		 *
		 * 4. If configured via parameter
		 * PARAM_HOMOGENEOUSGEOMETRIES_APPLY_ON_SUBTYPES:
		 *
		 * a) update the supertype(s) of FT so that it references the geometry
		 * type specific copies of FT instead of FT
		 *
		 * b) create copies of the subtype hierarchy of FT, one per geometry
		 * group that FT is split into (append the geometry specific suffix to
		 * each new class in the subtype hierarchy); update the supertype info
		 * for the direct subtypes of FT in each of these subtype hierarchies,
		 * so that it references the geometry specific copy of FT instead of FT
		 *
		 * c) update the subtype relationships in the geometry specific FT
		 * types, so that they reference the correct subtypes created in step
		 * 4b)
		 *
		 * d) create unions for all copies of FT, and likewise for the classes
		 * in the subtype hierarchy
		 *
		 * e) change the type of all attributes that used FT or one of its
		 * subtypes (in the complete hierarchy) as value type to the according
		 * union
		 *
		 * f) for each association where FT is at one end: establish a copy of
		 * the association for each geometry type specific copy of FT (updating
		 * type info and inClass appropriately) and append the geometry type
		 * specific suffix to the name of the role that has FT as value type
		 * (regardless whether it is navigable or not); set minimum multiplicity
		 * of the role with value type being a geometry specific type to 0
		 * (regardless whether it is navigable or not); pay attention to
		 * associations where both ends belong to feature types that have been
		 * split up; the type of attributes that are not in the selected schema
		 * is not updated - likewise, associations where one end is not part of
		 * the selected schema is not copied either
		 *
		 * g) remove FT and the original subtypes (in the complete hierarchy)
		 */

		Set<GenericClassInfo> classesToAdd = new HashSet<GenericClassInfo>();
		Set<GenericClassInfo> classesToRemove = new HashSet<GenericClassInfo>();

		/*
		 * Keep track of the copies with homogeneous geometries created for a
		 * specific class, as well as the geometry type specific copies created
		 * for the subtypes of these classes. The information will be used in
		 * step 4.d (when unions are created for each class that has been
		 * "fanned out") and 4.f (when associations are updated).
		 *
		 * key: original class; value: set of geometry specific copies of the
		 * class
		 */
		Map<GenericClassInfo, Map<String, GenericClassInfo>> classCopiesByGeometryTypeByOriginalClass = new HashMap<GenericClassInfo, Map<String, GenericClassInfo>>();

		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			Map<String, Set<GenericPropertyInfo>> propMapByGeomTypeName = new TreeMap<String, Set<GenericPropertyInfo>>();
			Map<GenericPropertyInfo, Set<GenericPropertyInfo>> propInfoSetsByGeomProperty = new HashMap<GenericPropertyInfo, Set<GenericPropertyInfo>>();

			if (genCi.category() != Options.FEATURE)
				continue;

			// identify all feature class properties that are of a geometry (ISO
			// 19107) type
			for (PropertyInfo pi : genCi.properties().values()) {

				String piTypeName = pi.typeInfo().name;

				if (piTypeName.startsWith("GM_")) {

					/*
					 * Add a new information set for the geometry typed
					 * property, which can be filled with associated properties
					 * later on.
					 *
					 * NOTE for cast: the cast should be safe, because pi
					 * belongs to a GenericClassInfo
					 */
					propInfoSetsByGeomProperty.put((GenericPropertyInfo) pi,
							new HashSet<GenericPropertyInfo>());

					/*
					 * Now add the property to the set of properties that belong
					 * to the same geometry type. Create a new set if necessary.
					 */
					if (propMapByGeomTypeName.containsKey(piTypeName)) {

						/*
						 * NOTE for cast: the cast should be safe, because pi
						 * belongs to a GenericClassInfo
						 */
						propMapByGeomTypeName.get(piTypeName)
								.add((GenericPropertyInfo) pi);

					} else {

						Set<GenericPropertyInfo> propInfoSetForGeomTypeName = new HashSet<GenericPropertyInfo>();

						/*
						 * NOTE for cast: the cast should be safe, because pi
						 * belongs to a GenericClassInfo
						 */
						propInfoSetForGeomTypeName
								.add((GenericPropertyInfo) pi);

						propMapByGeomTypeName.put(piTypeName,
								propInfoSetForGeomTypeName);
					}
				}
			}

			if (omitHomogeneousGeometriesForTypesWithSingleGeometryProperty
					&& propMapByGeomTypeName.size() <= 1) {
				/*
				 * fine - we leave this feature type alone, then; it only has
				 * geometry properties of a single type (or no such properties
				 * at all)
				 */
				continue;
			}

			/*
			 * if a supertype of genCi also has a geometry property and the rule
			 * shall be applied on subtypes as well, issue a warning and
			 * continue with the next class
			 */
			if (applyOnSubtypes && !propInfoSetsByGeomProperty.isEmpty()
					&& anySupertypeHasGeometryProperty(genCi)) {

				result.addWarning(null, 20313, genCi.name());
				continue;
			}

			// identify related properties
			for (GenericPropertyInfo geomTypeProp : propInfoSetsByGeomProperty
					.keySet()) {

				// we cannot identify related properties if the geometry typed
				// property has no prefix ending in the value of
				// separatorForPropertyFromNonUnion
				if (!geomTypeProp.name()
						.contains(separatorForPropertyFromNonUnion))
					continue;

				String prefix = geomTypeProp.name().substring(0, geomTypeProp
						.name().lastIndexOf(separatorForPropertyFromNonUnion));

				/*
				 * Look at each feature class property to see if it is related
				 * to the geometry typed property. If so, add it to the
				 * information set for that property. Do not add the geometry
				 * typed property itself, though.
				 */
				for (PropertyInfo pi : genCi.properties().values()) {

					if (pi.name().startsWith(prefix)
							&& pi.name() != geomTypeProp.name()) {

						/*
						 * NOTE for cast: the cast should be safe, because pi
						 * belongs to a GenericClassInfo
						 */
						propInfoSetsByGeomProperty.get(geomTypeProp)
								.add((GenericPropertyInfo) pi);
					}
				}
			}

			/*
			 * Identification of geometry typed properties and associated
			 * properties is done. Now create features with homogeneous geometry
			 * types according to information in type map entries.
			 */

			/*
			 * Keep track of the new geometry type specific copies of the
			 * feature type, together with the suffix assigned to them.
			 *
			 * key: geometry type specific suffix, value: feature copy created
			 * for the geometry type
			 */
			Map<String, GenericClassInfo> ftCopiesByGeometryTypeSuffix = new TreeMap<String, GenericClassInfo>();

			for (String geomType : propMapByGeomTypeName.keySet()) {

				// only process geometry typed properties for which a type
				// map entry is defined
				if (trfConfig.hasMappingForType(
						RULE_TRF_PROP_FLATTEN_HOMOGENEOUSGEOMETRIES,
						geomType)) {

					ProcessMapEntry mapEntry = trfConfig.getMappingForType(
							RULE_TRF_PROP_FLATTEN_HOMOGENEOUSGEOMETRIES,
							geomType);

					/*
					 * Remove all information sets that belong to one of the
					 * types from the type mapping that have no target types or
					 * no param. Also remove the according geometry properties
					 * themselves.
					 */
					if (!mapEntry.hasTargetType() || !mapEntry.hasParam()) {

						// TBD: this would also be the place to check geometry
						// profile infos: ensure that mapEntry.param
						// equalsIgnoreCase one of the geometry identifiers from
						// the profile and prevent creation of the
						// geometry-specific feature type if this condition is
						// not met

						for (GenericPropertyInfo geomTypeProperty : propMapByGeomTypeName
								.get(geomType)) {

							for (GenericPropertyInfo relatedProp : propInfoSetsByGeomProperty
									.get(geomTypeProperty)) {
								genModel.remove(relatedProp, false);
							}
							genModel.remove(geomTypeProperty, false);
						}

					} else {

						/*
						 * Create a copy of FT (NOTE: does not copy association
						 * roles), remove all properties from the other
						 * information sets in the copy, set the type of the
						 * property represented by S to the target type, and
						 * append the value of the type mapping parameter to the
						 * name and alias of the copy.
						 */

						GenericClassInfo featureCopy = genCi.createCopy(
								genCi.id() + separatorForGeometryTypeSuffix
										+ mapEntry.getParam(),
								genCi.name() + separatorForGeometryTypeSuffix
										+ mapEntry.getParam(),
								Options.FEATURE);
						/*
						 * NOTE: we have not added the properties of the copy to
						 * the model yet
						 */

						// keep track of the new copy
						ftCopiesByGeometryTypeSuffix.put(mapEntry.getParam(),
								featureCopy);

						// also keep track of it for step 4.d
						classCopiesByGeometryTypeByOriginalClass.put(genCi,
								ftCopiesByGeometryTypeSuffix);

						if (hasCode(genCi)) {
							setCode(featureCopy,
									getCode(genCi)
											+ separatorForGeometryTypeSuffix
											+ mapEntry.getParam());
						} else {
							setCode(featureCopy,
									genCi.name()
											+ separatorForGeometryTypeSuffix
											+ mapEntry.getParam());
						}

						/*
						 * Remove all properties in the feature copy that do not
						 * belong to the current geometry type. In order to do
						 * so, again loop through the list of geometry types
						 * identified for this feature type.
						 */
						for (String geomTypeToRemove : propMapByGeomTypeName
								.keySet()) {

							if (geomTypeToRemove.equals(geomType)) {
								continue;
							}

							for (GenericPropertyInfo geomTypePropertyToRemove : propMapByGeomTypeName
									.get(geomTypeToRemove)) {

								for (GenericPropertyInfo relatedPropToRemove : propInfoSetsByGeomProperty
										.get(geomTypePropertyToRemove)) {
									// NOTE: we deal with property copies, so it
									// is not safe to remove them by a simple
									// comparison of their references/pointers
									// via ==
									featureCopy.removeByStructuredNumber(
											relatedPropToRemove
													.sequenceNumber());
								}

								// NOTE: we deal with property copies, so it
								// is not safe to remove them by a simple
								// comparison of their references/pointers
								// via ==
								featureCopy.removeByStructuredNumber(
										geomTypePropertyToRemove
												.sequenceNumber());
							}
						}

						/*
						 * Now set the type of the remaining geometry typed
						 * properties (should all be of the same type now) from
						 * the feature copy to the target type, unless the type
						 * name and targetType are equal.
						 */
						for (PropertyInfo piFromFeatureCopy : featureCopy
								.properties().values()) {

							Type typeOfPi = piFromFeatureCopy.typeInfo();

							if (typeOfPi.name.equals(geomType)) {
								if (mapEntry.getTargetType()
										.equals(typeOfPi.name)) {
									// no need to update the type
									// information of the property,
									// because it is the same as the
									// target type
								} else {
									ClassInfo targetTypeCi = genModel
											.classByName(
													mapEntry.getTargetType());

									if (targetTypeCi != null) {
										typeOfPi.id = targetTypeCi.id();
										typeOfPi.name = targetTypeCi.name();
									} else {
										MessageContext mc = result.addWarning(
												null, 20302,
												mapEntry.getTargetType(),
												typeOfPi.name);
										if (mc != null)
											mc.addDetail(null, 20308,
													"Property",
													piFromFeatureCopy
															.fullName());
										typeOfPi.name = mapEntry
												.getTargetType();
									}
								}
							} else {
								// not a geometry typed property (just a
								// related one)
							}
						}

						classesToAdd.add(featureCopy);
						classesToRemove.add(genCi);

					}

				} else {
					// ignore properties with a geometry type for which no type
					// map entry is defined
				}
			}

			if (applyOnSubtypes && !propInfoSetsByGeomProperty.isEmpty()) {

				/*
				 * 4.a) update the supertypes of FT so that they reference the
				 * geometry type specific copies of FT instead of FT
				 */

				// identify supertypes of genCi / the feature type
				SortedSet<String> idsOfGenCiSupertypes = new TreeSet<String>();
				SortedSet<String> genCiSupertypes = genCi.supertypes();
				if (genCiSupertypes != null)
					idsOfGenCiSupertypes.addAll(genCiSupertypes);
				if (genCi.baseClass() != null) {
					idsOfGenCiSupertypes.add(genCi.baseClass().id());
				}

				for (String idOfGenCiSupertype : idsOfGenCiSupertypes) {

					ClassInfo supertype = genModel
							.classById(idOfGenCiSupertype);

					/*
					 * for safety against shallow copies of the supertype set,
					 * we use GenericClassInfo
					 */
					if (supertype instanceof GenericClassInfo) {

						GenericClassInfo supertypeGenCi = (GenericClassInfo) supertype;

						SortedSet<String> subtypesOfSupertype = supertypeGenCi
								.subtypes();

						// remove the id of genCi
						/*
						 * NOTE: we cannot just reset the subtype list of
						 * genCi's supertype, because the supertype may have
						 * other subtypes in addition to genCi itself
						 */
						subtypesOfSupertype.remove(genCi.id());

						/*
						 * add the ids of all geometry specific copies of genCi
						 * to the subtype list of the genCi supertype
						 */
						for (GenericClassInfo genCiCopy : ftCopiesByGeometryTypeSuffix
								.values()) {
							subtypesOfSupertype.add(genCiCopy.id());
						}

					} else {

						result.addWarning(null, 20312, supertype.name(),
								genCi.name());
					}
				}

				/*
				 * 4.b)create copies of the subtype hierarchy of FT, one per
				 * geometry group that FT is split into (append the geometry
				 * specific suffix to each new class in the subtype hierarchy);
				 * update the supertype info for the direct subtypes of FT in
				 * each of these subtype hierarchies, so that it references the
				 * geometry specific copy of FT instead of FT
				 *
				 * 4.c) update the subtype relationships in the geometry
				 * specific FT types, so that they reference the correct
				 * subtypes created in step 4.b)
				 */
				for (String suffix : ftCopiesByGeometryTypeSuffix.keySet()) {

					GenericClassInfo genCiCopy = ftCopiesByGeometryTypeSuffix
							.get(suffix);

					createSubtypeHierarchyCopyForClassCopy(genCi, genCiCopy,
							separatorForGeometryTypeSuffix + suffix,
							classesToAdd, classesToRemove,
							classCopiesByGeometryTypeByOriginalClass);
				}
			}
		}

		/*
		 * 4.d) create unions for all copies of FT, and likewise for the classes
		 * in the subtype hierarchy
		 */
		Map<String, GenericClassInfo> copiedClassUnionsByOriginalClassId = new TreeMap<String, GenericClassInfo>();

		for (GenericClassInfo copiedClass : classCopiesByGeometryTypeByOriginalClass
				.keySet()) {

			Map<String, GenericClassInfo> classCopiesByGeometryType = classCopiesByGeometryTypeByOriginalClass
					.get(copiedClass);

			GenericClassInfo copiedClassUnion = new GenericClassInfo(genModel,
					copiedClass.id() + "_union", copiedClass.name() + "Union",
					Options.UNION);

			if (hasCode(copiedClass)) {
				setCode(copiedClassUnion, getCode(copiedClass) + "_U");
			} else {
				setCode(copiedClassUnion, copiedClass.name() + "Union");
			}

			// TBD it would be good to use java enums for stereotypes
			copiedClassUnion.setStereotype("union");
			copiedClassUnion.setPkg(copiedClass.pkg());

			/*
			 * NOTE for cast: the cast should be safe, because the package of
			 * copiedClassUnion is that of the copiedClass, and class copies are
			 * only created for GenericClassInfos
			 */
			((GenericPackageInfo) copiedClassUnion.pkg())
					.addClass(copiedClassUnion);

			// add property for each geometry specific copy of the class

			int seqNumIndex = 1;
			for (String suffix : classCopiesByGeometryType.keySet()) {

				GenericClassInfo classCopy = classCopiesByGeometryType
						.get(suffix);

				GenericPropertyInfo copiedClassUnionProp = new GenericPropertyInfo(
						genModel,
						copiedClassUnion.id() + "_choice" + seqNumIndex,
						normaliseGeometryTypeSuffix(suffix),
						classCopy.category());

				copiedClassUnionProp.setStereotype("");

				Type propType = new Type();
				propType.id = classCopy.id();
				propType.name = classCopy.name();
				copiedClassUnionProp.setTypeInfo(propType);

				/*
				 * TODO: configure non-standard tagged values via configuration
				 * parameter
				 */
				TaggedValues taggedValues = options.taggedValueFactory();
				taggedValues.add("gmlImplementedByNilReason", "false");
				taggedValues.add("inlineOrByReference", "inlineOrByReference");
				taggedValues.add("isMetadata", "false");
				taggedValues.add(PARAM_MAXOCCURS, "");
				taggedValues.add("modified", "");
				taggedValues.add("name", "");
				taggedValues.add("physicalQuantity", "");
				taggedValues.add("profiles", "");
				taggedValues.add("recommendedMeasure", "");
				taggedValues.add("securityClassification", "");
				taggedValues.add("sequenceNumber", "" + seqNumIndex);
				taggedValues.add("xsdEncodingRule", "");
				copiedClassUnionProp.setTaggedValues(taggedValues, false);

				/*
				 * No need to update the "sequenceNumber" tagged value because
				 * we just explicitly set it.
				 */
				copiedClassUnionProp.setSequenceNumber(
						new StructuredNumber("" + seqNumIndex), false);

				copiedClassUnionProp.setInClass(copiedClassUnion);

				/*
				 * Careful: we created a new set of tagged values for the new
				 * property. Thus all modifications to the new property that
				 * might impact the tagged values, such as setting the code
				 * value, need to be performed after the new set of tagged
				 * values has been set
				 */
				// if (hasCode(genCiSub)) {
				// setCode(genSuperClassUnionProp,
				// toLowerCase(getCode(genCiSub)));
				// } else {
				// setCode(genSuperClassUnionProp,
				// toLowerCase(genCiSub.name()));
				// }

				copiedClassUnion.addProperty(copiedClassUnionProp,
						PropertyCopyDuplicatBehaviorIndicator.ADD);
				seqNumIndex++;

			}

			copiedClassUnionsByOriginalClassId.put(copiedClass.id(),
					copiedClassUnion);

			classesToAdd.add(copiedClassUnion);
		}

		/*
		 * 4.e) change the type of all attributes that used FT or one of its
		 * subtypes (in the complete hierarchy) as type to the according union
		 */
		// update type in all attributes of classes that the model knows
		// of
		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			// ignore association roles
			if (!genPi.isAttribute())
				continue;

			Type type = genPi.typeInfo();

			if (copiedClassUnionsByOriginalClassId.containsKey(type.id)) {

				genPi.setCategoryOfValue(Options.UNION);

				GenericClassInfo copiedClassUnion = copiedClassUnionsByOriginalClassId
						.get(type.id);

				type.name = copiedClassUnion.name();
				type.id = copiedClassUnion.id();
			}
		}

		/*
		 * also update type in all attributes of classes that the model does not
		 * know of yet
		 */
		for (GenericClassInfo genCiToAdd : classesToAdd) {

			for (PropertyInfo pi : genCiToAdd.properties().values()) {

				if (!pi.isAttribute())
					continue;

				Type type = pi.typeInfo();

				if (copiedClassUnionsByOriginalClassId.containsKey(type.id)) {

					GenericClassInfo copiedClassUnion = copiedClassUnionsByOriginalClassId
							.get(type.id);

					/*
					 * NOTE for cast: the cast should be safe, because pi
					 * belongs to a GenericClassInfo (genCiToAdd)
					 */
					GenericPropertyInfo genPi = (GenericPropertyInfo) pi;

					genPi.setCategoryOfValue(Options.UNION);

					type.name = copiedClassUnion.name();
					type.id = copiedClassUnion.id();
				}
			}
		}

		/*
		 * 4.f) - see description further above
		 */

		Set<GenericAssociationInfo> genAisToAdd = new HashSet<GenericAssociationInfo>();
		Set<GenericPropertyInfo> propertiesToRemove = new HashSet<GenericPropertyInfo>();

		for (GenericAssociationInfo genAi : genModel
				.selectedSchemaAssociations()) {

			PropertyInfo pi1 = genAi.end1();
			PropertyInfo pi2 = genAi.end2();

			String name1 = pi1.inClass().name();
			String name2 = pi2.inClass().name();

			/*
			 * check that at least one end of the association belongs to a class
			 * that has been split
			 */
			if (!(classCopiesByGeometryTypeByOriginalClass
					.containsKey(pi1.inClass())
					|| classCopiesByGeometryTypeByOriginalClass
							.containsKey(pi2.inClass()))) {

				continue;
			}

			/*
			 * check that both association ends are part of the selected schema
			 */
			if (!(pi1 instanceof GenericPropertyInfo
					&& pi2 instanceof GenericPropertyInfo)) {
				/*
				 * log warning, ensuring that class names to describe the
				 * association are in lexicographical order so that the warning
				 * is not logged twice (log message has prefix '??' to prevent
				 * duplicate messages) which is relevant in case there are
				 * multiple associations between the two classes
				 */
				if (!(pi1 instanceof GenericPropertyInfo)) {

					result.addWarning(null, 20333,
							(name1.compareTo(name2) <= 0) ? name1 : name2,
							(name1.compareTo(name2) <= 0) ? name2 : name1,
							name1);

				} else {

					// pi2 is not an instance of GenericPropertyInfo
					result.addWarning(null, 20333,
							(name1.compareTo(name2) <= 0) ? name1 : name2,
							(name1.compareTo(name2) <= 0) ? name2 : name1,
							name2);
				}

				continue;
			}

			// ================================================================
			// fine, we need to create copies of the association

			/*
			 * Note on cast: safe because the ends of a GenericAssociationInfo
			 * should be of type GenericPropertyInfo
			 */
			GenericPropertyInfo genPi1Orig = (GenericPropertyInfo) pi1;
			GenericPropertyInfo genPi2Orig = (GenericPropertyInfo) pi2;

			propertiesToRemove.add(genPi1Orig);
			propertiesToRemove.add(genPi2Orig);

			// now create the new associations

			String separator = separatorForPropertyFromUnion;

			if (classCopiesByGeometryTypeByOriginalClass
					.containsKey(pi1.inClass())
					&& classCopiesByGeometryTypeByOriginalClass
							.containsKey(pi2.inClass())) {

				// both ends have been split

				Map<String, GenericClassInfo> classCopiesPi1InClass = classCopiesByGeometryTypeByOriginalClass
						.get(pi1.inClass());
				Map<String, GenericClassInfo> classCopiesPi2InClass = classCopiesByGeometryTypeByOriginalClass
						.get(pi2.inClass());

				// check that the maps are not empty
				boolean checkFailed = false;
				if (classCopiesPi1InClass == null
						|| classCopiesPi1InClass.isEmpty()) {
					checkFailed = true;
					result.addError(null, 20335, pi1.inClass().name());
				}
				if (classCopiesPi2InClass == null
						|| classCopiesPi2InClass.isEmpty()) {
					checkFailed = true;
					result.addError(null, 20335, pi2.inClass().name());
				}
				if (checkFailed) {
					continue;
				}

				// create association copies

				/*
				 * both maps with class copies are ordered - when we iterate
				 * through these sets, it is fine to have an overall index for
				 * the sequence number suffix; each association copy that is
				 * created will then be placed in a well-defined order that is
				 * defined by the sorted keys in both maps
				 */
				int sequencNumberIndex = 1;

				for (String s1 : classCopiesPi1InClass.keySet()) {

					GenericClassInfo copyPi1InClass = classCopiesPi1InClass
							.get(s1);

					for (String s2 : classCopiesPi2InClass.keySet()) {

						GenericClassInfo copyPi2InClass = classCopiesPi2InClass
								.get(s2);

						Multiplicity mPi1 = new Multiplicity(
								pi1.cardinality().toString());
						mPi1.minOccurs = 0;

						Multiplicity mPi2 = new Multiplicity(
								pi2.cardinality().toString());
						mPi2.minOccurs = 0;

						// compute new name and code/alias
						String newNamePi1 = pi1.name() + separator + s2;
						String newNamePi2 = pi2.name() + separator + s1;

						String newAliasPi1 = (hasCode(pi1))
								? getCode(pi1) + separator + s2 : null;
						String newAliasPi2 = (hasCode(pi2))
								? getCode(pi2) + separator + s1 : null;

						StructuredNumber newSnPi1 = pi1.sequenceNumber()
								.createCopyWithSuffix(sequencNumberIndex);
						StructuredNumber newSnPi2 = pi2.sequenceNumber()
								.createCopyWithSuffix(sequencNumberIndex);
						sequencNumberIndex++;

						// create association copy
						GenericAssociationInfo aiCopy = createCopyAndSetEnds(
								genModel, genAi, newNamePi1, newAliasPi1,
								copyPi1InClass, mPi1, newSnPi1, newNamePi2,
								newAliasPi2, copyPi2InClass, mPi2, newSnPi2,
								false);

						genAisToAdd.add(aiCopy);
					}
				}

			} else {

				// only one end has been split

				if (classCopiesByGeometryTypeByOriginalClass
						.containsKey(pi1.inClass())) {

					Map<String, GenericClassInfo> classCopiesPi1InClass = classCopiesByGeometryTypeByOriginalClass
							.get(pi1.inClass());

					if (classCopiesPi1InClass == null
							|| classCopiesPi1InClass.isEmpty()) {

						result.addError(null, 20335, pi1.inClass().name());

					} else {

						/*
						 * to be one the safe side, append a suffix to the
						 * sequence numbers of the ends in each association copy
						 * that is created
						 */
						int sequencNumberIndex = 1;
						for (String s1 : classCopiesPi1InClass.keySet()) {

							GenericClassInfo copyPi1InClass = classCopiesPi1InClass
									.get(s1);

							Multiplicity mPi2 = new Multiplicity(
									pi2.cardinality().toString());
							mPi2.minOccurs = 0;

							/*
							 * Note on cast: should be safe because we checked
							 * before that pi2 is an instance of
							 * GenericPropertyInfo, and its inClass should thus
							 * be a GenericClassInfo
							 */
							GenericClassInfo pi2InClass = (GenericClassInfo) pi2
									.inClass();

							// compute new name and code/alias
							String newNamePi2 = pi2.name() + separator + s1;
							String newAliasPi2 = (hasCode(pi2))
									? getCode(pi2) + separator + s1 : null;

							StructuredNumber newSnPi1 = pi1.sequenceNumber()
									.createCopyWithSuffix(sequencNumberIndex);
							StructuredNumber newSnPi2 = pi2.sequenceNumber()
									.createCopyWithSuffix(sequencNumberIndex);
							sequencNumberIndex++;

							// create association copy
							GenericAssociationInfo aiCopy = createCopyAndSetEnds(
									genModel, genAi, null, null, copyPi1InClass,
									null, newSnPi1, newNamePi2, newAliasPi2,
									pi2InClass, mPi2, newSnPi2, false);

							genAisToAdd.add(aiCopy);
						}
					}

				} else {

					Map<String, GenericClassInfo> classCopiesPi2InClass = classCopiesByGeometryTypeByOriginalClass
							.get(pi2.inClass());

					if (classCopiesPi2InClass == null
							|| classCopiesPi2InClass.isEmpty()) {

						result.addError(null, 20335, pi2.inClass().name());

					} else {

						/*
						 * to be one the safe side, append a suffix to the
						 * sequence numbers of the ends in each association copy
						 * that is created
						 */
						int sequencNumberIndex = 1;
						for (String s2 : classCopiesPi2InClass.keySet()) {

							GenericClassInfo copyPi2InClass = classCopiesPi2InClass
									.get(s2);

							Multiplicity mPi1 = new Multiplicity(
									pi1.cardinality().toString());
							mPi1.minOccurs = 0;

							/*
							 * Note on cast: should be safe because we checked
							 * before that pi1 is an instance of
							 * GenericPropertyInfo, and its inClass should thus
							 * be a GenericClassInfo
							 */
							GenericClassInfo pi1InClass = (GenericClassInfo) pi1
									.inClass();

							// compute new name and code/alias
							String newNamePi1 = pi1.name() + separator + s2;

							String newAliasPi1 = (hasCode(pi1))
									? getCode(pi1) + separator + s2 : null;

							StructuredNumber newSnPi1 = pi1.sequenceNumber()
									.createCopyWithSuffix(sequencNumberIndex);
							StructuredNumber newSnPi2 = pi2.sequenceNumber()
									.createCopyWithSuffix(sequencNumberIndex);
							sequencNumberIndex++;

							// create association copy
							GenericAssociationInfo aiCopy = createCopyAndSetEnds(
									genModel, genAi, newNamePi1, newAliasPi1,
									pi1InClass, mPi1, newSnPi1, null, null,
									copyPi2InClass, null, newSnPi2, false);

							genAisToAdd.add(aiCopy);
						}
					}
				}
			}
		}

		/*
		 * 4.g) remove FT and the original subtypes (in the complete hierarchy)
		 * -> should be done at the end, when all classes stored in the
		 * featuretypesToRemove set are removed from the model
		 */

		// now add the feature type copies to the model (automatically adds them
		// to their packages and also adds their properties to the
		// model
		for (GenericClassInfo featureCopy : classesToAdd) {
			genModel.addClass(featureCopy);
		}

		// also remove specific properties
		for (GenericPropertyInfo piToRemove : propertiesToRemove) {
			genModel.remove(piToRemove, false);
		}

		// also add association copies to the model
		for (GenericAssociationInfo aiCopy : genAisToAdd) {
			genModel.addAssociation(aiCopy);
		}

		// remove copied feature types from model
		for (GenericClassInfo copiedFeature : classesToRemove) {
			genModel.remove(copiedFeature);
		}

	}

	/**
	 * @param genCi
	 * @return true if any of the supertypes of genCi (to the highest level) has
	 *         a property where the name of the type starts with "GM_", else
	 *         false
	 */
	private boolean anySupertypeHasGeometryProperty(ClassInfo genCi) {

		Set<String> supertypesOfGenCi = new HashSet<String>();
		if (genCi.supertypes() != null) {
			supertypesOfGenCi.addAll(genCi.supertypes());
		}
		if (genCi.baseClass() != null) {
			supertypesOfGenCi.add(genCi.baseClass().id());
		}

		Model model = genCi.model();

		for (String supertypeId : supertypesOfGenCi) {

			ClassInfo supertypeCi = model.classById(supertypeId);

			for (PropertyInfo pi : supertypeCi.properties().values()) {

				if (pi.typeInfo().name.startsWith("GM_")) {
					return true;
				}
			}

			boolean supertypeOfSupertypeHasGeomProp = anySupertypeHasGeometryProperty(
					supertypeCi);
			if (supertypeOfSupertypeHasGeomProp) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Removes all non-word characters and the underscores in the given suffix,
	 * and completely turns the resulting string into lower case (according to
	 * english locale).
	 *
	 * @param suffix
	 * @return
	 */
	private String normaliseGeometryTypeSuffix(String suffix) {

		String result = suffix.replaceAll("\\W|_", "");
		result = result.toLowerCase(Locale.ENGLISH);
		return result;
	}

	/**
	 *
	 * @param genCi
	 *            the original class
	 * @param genCiCopy
	 *            the copy of the class, for which a copy of the subtype
	 *            hierarchy of the original class shall be create
	 * @param suffix
	 *            to be added to the names and IDs of all classes in the subtype
	 *            hierarchy copy
	 * @param classesToAdd
	 *            set to keep track of subtype copies that have been created and
	 *            thus shall be added to the model at the end of processing
	 * @param classesToRemove
	 *            set to keep track of the subtype classes that have been copied
	 *            and thus shall be removed from the model at the end of
	 *            processing
	 * @param classCopiesByOriginalClass
	 *            used to keep track of the copies created for a specific class
	 */
	private void createSubtypeHierarchyCopyForClassCopy(GenericClassInfo genCi,
			GenericClassInfo genCiCopy, String suffix,
			Set<GenericClassInfo> classesToAdd,
			Set<GenericClassInfo> classesToRemove,
			Map<GenericClassInfo, Map<String, GenericClassInfo>> classCopiesByGeometryTypeByOriginalClass) {

		GenericModel genModel = genCiCopy.model();

		@SuppressWarnings("unchecked")
		SortedSet<String> subtypeIds = (TreeSet<String>) genCiCopy.subtypes()
				.clone();

		for (String subtypeId : subtypeIds) {

			ClassInfo subCi = genModel.classById(subtypeId);

			if (subCi == null || !(subCi instanceof GenericClassInfo)) {

				// we can't copy this part of the subtype hierarchy
				result.addError(null, 20311, genCi.name(), subtypeId);

			} else {

				GenericClassInfo subGenCi = (GenericClassInfo) subCi;

				/*
				 * Create a copy of the subtype.
				 */
				GenericClassInfo subtypeCopy = subGenCi.createCopy(
						subGenCi.id() + suffix, subGenCi.name() + suffix,
						subGenCi.category());

				// NOTE: we have not added the properties of the copy to
				// the model yet; they should be added when the classes
				// contained in the classesToAdd set are added to the model
				// (external to this method)

				// keep track of the new copy
				classesToAdd.add(subtypeCopy);
				classesToRemove.add(subGenCi);

				// add the copy to the list of copies created for the subtype
				Map<String, GenericClassInfo> classCopiesByGeometryType;
				if (classCopiesByGeometryTypeByOriginalClass
						.containsKey(subGenCi)) {

					classCopiesByGeometryType = classCopiesByGeometryTypeByOriginalClass
							.get(subGenCi);

				} else {
					classCopiesByGeometryType = new TreeMap<String, GenericClassInfo>();
					classCopiesByGeometryTypeByOriginalClass.put(subGenCi,
							classCopiesByGeometryType);
				}
				classCopiesByGeometryType.put(suffix, subtypeCopy);

				// append the suffix to the name and alias/code of the copy
				if (hasCode(subGenCi)) {
					setCode(subtypeCopy, getCode(subGenCi) + suffix);
				} else {
					setCode(subtypeCopy, subGenCi.name() + suffix);
				}

				/*
				 * update the generalization relationships for genCiCopy and
				 * subtypeCopy (those in genCi and subGenCi don't need to be
				 * updated because these classes will be removed from the model
				 * later, though that is external to this method)
				 */

				// replace genCi.id with genCiCopy.id in the supertype
				// info of subtypeCopy; if the baseClass of subtypeCopy is
				// genCi, set it to genCiCopy
				subtypeCopy.updateSupertypeId(genCi.id(), genCiCopy.id());
				if (subtypeCopy.baseClass() != null
						&& subtypeCopy.baseClass().id().equals(genCi.id())) {
					subtypeCopy.setBaseClass(genCiCopy);
				}

				// update the subtype info in all supertypes of subtypeCopy
				// replace subGenCi.id with subtypeCopy.id in the subtype list

				// WARNING: this must occur after the supertype info in
				// subtypeCopy has been updated, otherwise the subtype info in
				// genCiCopy would not be updated correctly
				SortedSet<String> idsOfSubtypeCopySupertypes = subtypeCopy
						.supertypes();
				for (String idOfSubtypeCopySupertype : idsOfSubtypeCopySupertypes) {

					ClassInfo supertypeOfSubtypeCopy = genModel
							.classById(idOfSubtypeCopySupertype);

					if (supertypeOfSubtypeCopy == null) {
						// this can happen for the case in which the supertype
						// is genCiCopy, i.e. a supertype which has not been
						// added to the model yet
						for (GenericClassInfo classToAdd : classesToAdd) {
							if (classToAdd.id()
									.equals(idOfSubtypeCopySupertype)) {
								supertypeOfSubtypeCopy = classToAdd;
							}
						}
					}

					if (supertypeOfSubtypeCopy == null) {

						// now this is unexpected
						result.addError(null, 20314, idOfSubtypeCopySupertype,
								subtypeCopy.name());

					} else if (!(supertypeOfSubtypeCopy instanceof GenericClassInfo)) {

						result.addError(null, 20322,
								supertypeOfSubtypeCopy.name(),
								subtypeCopy.name(), subGenCi.id(),
								subtypeCopy.id());

					} else {

						((GenericClassInfo) supertypeOfSubtypeCopy)
								.updateSubtypeId(subGenCi.id(),
										subtypeCopy.id());
					}
				}

				/*
				 * alright, that should be all for copying this subtype and
				 * establishing the relationships to its supertype(s); now
				 * create a copy of the subtypes of the subtype
				 */
				createSubtypeHierarchyCopyForClassCopy(subGenCi, subtypeCopy,
						suffix, classesToAdd, classesToRemove,
						classCopiesByGeometryTypeByOriginalClass);
			}
		}
	}

	/**
	 * Processes all class and property constraints: the Flattener keeps only
	 * those that have a textual comment embedded in “\/*” and “*\/” and reduces
	 * these constraints to this textual comment (starting with “\/*” and ending
	 * with “*\/”).
	 *
	 * The Flattener also ensures that duplicates of a class or property
	 * constraint, i.e. any constraint that has the same text value as another
	 * constraint, are removed - HOWEVER, this is achieved during postprocessing
	 * phase.
	 *
	 * Note: at the moment this only processes constraints belonging to classes
	 * and properties of the application schema.
	 *
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleClsFlattenConstraints(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		// create copies of constraints in all app schema classes
		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			if (genCi.hasConstraints()) {

				List<Constraint> classConstraints = genCi.constraints();
				Vector<Constraint> newConstraints = new Vector<Constraint>();

				for (Constraint origCon : classConstraints) {

					String text = origCon.text();

					if (text != null && text.contains("/*")) {

						String textUpdate = text
								.replaceAll("/\\*|\\*/[\\w|\\W]*", "");

						GenericTextConstraint genCon = new GenericTextConstraint(
								genCi, origCon);
						genCon.setText(textUpdate);
						newConstraints.add(genCon);
					}
				}
				genCi.setConstraints(newConstraints);
			}
		}

		// create copies of constraints in all app schema properties
		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			if (genPi.hasConstraints()) {

				List<Constraint> propConstraints = genPi.constraints();
				List<Constraint> newConstraints = new Vector<Constraint>();

				for (Constraint origCon : propConstraints) {

					String text = origCon.text();

					if (text != null && text.contains("/*")) {

						String textUpdate = text.substring(text.indexOf("/*"),
								text.indexOf("*/") + 2);

						GenericTextConstraint genCon = new GenericTextConstraint(
								genPi, origCon);
						genCon.setText(textUpdate);

						newConstraints.add(genCon);
					}
				}

				genPi.setConstraints(newConstraints);
			}
		}
	}

	private void applyRuleAllFlattenName(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		/*
		 * determine if code values for properties should be converted to lower
		 * case; TRANSFORMER_LOWER_CASE_ALIAS_FOR_PROPERTIES is kept for
		 * backward compatibility
		 */
		boolean lowerCaseCodeForProperties = false;
		if (trfConfig.hasParameter(TRANSFORMER_LOWER_CASE_ALIAS_FOR_PROPERTIES)
				|| trfConfig.hasParameter(
						TRANSFORMER_LOWER_CASE_CODE_FOR_PROPERTIES)) {

			String paramValue_alias = trfConfig.getParameterValue(
					TRANSFORMER_LOWER_CASE_ALIAS_FOR_PROPERTIES);

			String paramValue_code = trfConfig.getParameterValue(
					TRANSFORMER_LOWER_CASE_CODE_FOR_PROPERTIES);

			if (paramValue_alias != null) {
				lowerCaseCodeForProperties |= Boolean
						.parseBoolean(paramValue_alias);
			} else if (paramValue_code != null) {
				lowerCaseCodeForProperties |= Boolean
						.parseBoolean(paramValue_code);
			}
		}

		/*
		 * determine if code values should also be used for names of enumeration
		 * values; TRANSFORMER_ALIAS_FOR_ENUMERATION_VALUES is kept for backward
		 * compatibility
		 */
		boolean codeForEnumerationValues = true;
		if (trfConfig.hasParameter(TRANSFORMER_ALIAS_FOR_ENUMERATION_VALUES)
				|| trfConfig.hasParameter(
						TRANSFORMER_CODE_FOR_ENUMERATION_VALUES)) {

			String paramValue_alias = trfConfig.getParameterValue(
					TRANSFORMER_ALIAS_FOR_ENUMERATION_VALUES);

			String paramValue_code = trfConfig
					.getParameterValue(TRANSFORMER_CODE_FOR_ENUMERATION_VALUES);

			if (paramValue_alias != null) {
				codeForEnumerationValues &= Boolean
						.parseBoolean(paramValue_alias);
			} else if (paramValue_code != null) {
				codeForEnumerationValues &= Boolean
						.parseBoolean(paramValue_code);
			}
		}

		/*
		 * By default the original name of a model element is stored in its
		 * code. However, this behavior can be turned off via the configuration.
		 * TRANSFORMER_KEEP_ORIGINAL_NAME_AS_ALIAS is kept for backward
		 * compatibility.
		 */
		boolean keepOriginalNameAsCode = true;

		if (trfConfig.hasParameter(TRANSFORMER_KEEP_ORIGINAL_NAME_AS_ALIAS)
				|| trfConfig
						.hasParameter(TRANSFORMER_KEEP_ORIGINAL_NAME_AS_CODE)) {

			String paramValue_alias = trfConfig
					.getParameterValue(TRANSFORMER_KEEP_ORIGINAL_NAME_AS_ALIAS);

			String paramValue_code = trfConfig
					.getParameterValue(TRANSFORMER_KEEP_ORIGINAL_NAME_AS_CODE);

			if (paramValue_alias != null) {
				keepOriginalNameAsCode &= Boolean
						.parseBoolean(paramValue_alias);
			}

			if (paramValue_code != null) {
				keepOriginalNameAsCode &= Boolean.parseBoolean(paramValue_code);
			}
		}

		// update class names
		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			// get (trimmed) code for the class
			String code = getCode(genCi);

			// only update if a code is actually available for the class
			if (code != null) {

				String oldName = genCi.name();

				if (keepOriginalNameAsCode) {
					// set the name of the class as its code value
					setCode(genCi, oldName);
				}

				genModel.updateClassName(genCi, code);
			}
		}

		// update property names - also for any reverse properties
		Set<GenericPropertyInfo> genPis = new HashSet<GenericPropertyInfo>();

		for (PropertyInfo pi : genModel.selectedSchemaProperties()) {

			/*
			 * note on cast: safe
			 */
			GenericPropertyInfo genPi = (GenericPropertyInfo) pi;

			genPis.add(genPi);

			if (genPi.reverseProperty() != null) {
				/*
				 * note on cast: safe
				 */
				GenericPropertyInfo revGenPi = (GenericPropertyInfo) genPi
						.reverseProperty();
				genPis.add(revGenPi);
			}
		}

		for (GenericPropertyInfo genPi : genPis) {

			// get (trimmed) code for the property
			String code = getCode(genPi);

			// only update if a code is actually available for the property
			if (code != null) {

				/*
				 * skip if this is an enumeration property and
				 * codeForEnumerationValues configuration parameter is false
				 */
				if (genPi.inClass().category() == Options.ENUMERATION
						&& !codeForEnumerationValues) {
					continue;
				}

				/*
				 * there should be no specific places (e.g. maps that use the
				 * property name as key) in other classes where the property
				 * name occurs, thus we can simply replace the name with the
				 * code
				 */

				// finally, udpate the name in the property itself
				String name = genPi.name();

				if (lowerCaseCodeForProperties) {
					code = code.toLowerCase(Locale.ENGLISH);
				}

				genPi.setName(code);

				if (keepOriginalNameAsCode) {
					setCode(genPi, name);
				}
			}
		}

		/*
		 * TODO: update code of app schema packages; update code of association
		 * infos (association property names are updated)
		 */
	}

	/**
	 * Sets the code value of the given property.
	 *
	 * Automatically determines where to store the code value - in the alias or
	 * a specific tagged value.
	 *
	 * @param genPi
	 * @param codeValue
	 */
	private void setCode(GenericPropertyInfo genPi, String codeValue) {

		if (tvNameForCodeValue == null) {

			// store in the alias
			genPi.setAliasName(codeValue);

		} else {

			// store in a tagged value
			genPi.setTaggedValue(tvNameForCodeValue, codeValue, false);
		}
	}

	/**
	 * Sets the code value of the given class.
	 *
	 * Automatically determines where to store the code value - in the alias or
	 * a specific tagged value.
	 *
	 * @param genCi
	 * @param codeValue
	 */
	private void setCode(GenericClassInfo genCi, String codeValue) {

		if (tvNameForCodeValue == null) {

			// store in the alias
			genCi.setAliasName(codeValue);

		} else {

			// store in a tagged value
			genCi.setTaggedValue(tvNameForCodeValue, codeValue, false);
		}
	}

	/**
	 * Determines the code to be used when flattening the name of an Info
	 * object. By default this is the alias. However, it can also be a specific
	 * tagged value.
	 *
	 * @param info
	 *            Info object contained in the model, for which to determine the
	 *            code.
	 * @return The (trimmed) code for the Info object or <code>null</code> if
	 *         the source of the code does not exist or is empty.
	 */
	private String getCode(Info info) {

		String code = null;

		if (tvNameForCodeValue != null) {
			code = info.taggedValue(tvNameForCodeValue);
		} else {
			code = info.aliasName();
		}

		if (code != null) {
			if (code.trim().length() == 0) {
				code = null;
			} else {
				code = code.trim();
			}
		}

		return code;
	}

	private void applyRuleUnionReplace(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		// compute some general parameter values
		boolean includeUnionIdentifierTV = false;
		if (trfConfig.hasParameter(PARAM_INCLUDE_UNION_IDENTIFIER_TV)) {
			String tmp = trfConfig
					.getParameterValue(PARAM_INCLUDE_UNION_IDENTIFIER_TV);
			if (tmp.trim().equalsIgnoreCase("true")) {
				includeUnionIdentifierTV = true;
			}
		}

		Pattern replaceUnionExcludePattern = null;
		if (trfConfig.hasParameter(PARAM_REPLACE_UNION_EXCLUDE_REGEX)) {
			String replaceUnionExcludeRegex = trfConfig
					.getParameterValue(PARAM_REPLACE_UNION_EXCLUDE_REGEX);
			replaceUnionExcludePattern = Pattern
					.compile(replaceUnionExcludeRegex);
		}

		// identify all union classes
		SortedSet<GenericClassInfo> unionsToProcess = new TreeSet<GenericClassInfo>();

		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			if (genCi.category() == Options.UNION) {

				if (replaceUnionExcludePattern != null) {

					Matcher m = replaceUnionExcludePattern
							.matcher(genCi.name());

					if (m.matches()) {
						/*
						 * alright, then the type shall be excluded from
						 * processing
						 */
						result.addDebug(null, 20344, genCi.name(), replaceUnionExcludePattern.pattern(), PARAM_REPLACE_UNION_EXCLUDE_REGEX);
					} else {
						result.addDebug(null, 20345, genCi.name(), replaceUnionExcludePattern.pattern(), PARAM_REPLACE_UNION_EXCLUDE_REGEX);
						unionsToProcess.add(genCi);
					}

				} else {
					unionsToProcess.add(genCi);
				}
			}
		}

		/*
		 * identify union classes that were used to replace properties, so that
		 * they can be removed at the end if they are no longer used in the
		 * model
		 */
		Map<String, GenericClassInfo> processedUnionsById = new HashMap<String, GenericClassInfo>();

		for (GenericClassInfo union : unionsToProcess) {

			List<GenericPropertyInfo> propsToAdd = new ArrayList<GenericPropertyInfo>();
			Set<GenericPropertyInfo> propsToRemove = new HashSet<GenericPropertyInfo>();

			for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

				if (genCi == union) {
					continue;
				}

				int countPropsWithUnionAsValueType = 0;
				GenericPropertyInfo relPi = null;

				for (PropertyInfo pi : genCi.properties().values()) {

					if (!pi.isNavigable()) {
						continue;
					}

					if (pi.typeInfo().id.equals(union.id())) {
						countPropsWithUnionAsValueType++;
						if (pi.cardinality().maxOccurs == 1) {
							relPi = (GenericPropertyInfo) pi;
						}
					}
				}

				if (countPropsWithUnionAsValueType == 1 && relPi != null) {

					/*
					 * replace the relevant property with copies of the union
					 * options
					 */

					processedUnionsById.put(union.id(), union);

					int seqNumIndex = 1;
					for (PropertyInfo uPi : union.properties().values()) {

						GenericPropertyInfo uGPi = (GenericPropertyInfo) uPi;

						GenericPropertyInfo copy = uGPi.createCopy(relPi.id()
								+ "_replacedByUnionProperty_" + uGPi.name());

						copy.setInClass(genCi);

						// merge global identifier information
						if (genCi.globalId() == null) {
							/*
							 * globalId from uGPi can be used as-is, which is
							 * the default for the copy
							 */
						} else if (uGPi.globalId() == null) {

							// use the global id from genPi
							copy.setGlobalId(relPi.globalId());

						} else {

							// merge global ids
							copy.setGlobalId(
									relPi.globalId() + "." + uGPi.globalId());
						}

						/* handle derived properties */
						if (relPi.isDerived()) {
							copy.setDerived(true);
						}

						/*
						 * ensure that the copy is not counted as an association
						 * role
						 */
						copy.setAttribute(true);
						copy.setAssociation(null);

						/*
						 * set union identifier if so configured and if the
						 * class that the property is copied to is not a union
						 * itself; we have already checked that max multiplicity
						 * of the property that is being replaced is 1
						 */
						if (includeUnionIdentifierTV
								&& !(genCi.category() == Options.UNION)) {
							TaggedValues tvs = copy.taggedValuesAll();
							tvs.put(UNION_SET_TAG_NAME, relPi.name());
							copy.setTaggedValues(tvs, false);
						}

						/*
						 * ensure that "sequenceNumber" tagged value is also
						 * updated
						 */
						copy.setSequenceNumber(relPi.sequenceNumber()
								.createCopyWithSuffix(seqNumIndex), true);
						seqNumIndex++;

						int minOccurs = relPi.cardinality().minOccurs
								* uGPi.cardinality().minOccurs;
						int genPiMaxOccurs = relPi.cardinality().maxOccurs;
						int typeGPiMaxOccurs = uGPi.cardinality().maxOccurs;
						int maxOccurs = 0;
						if (genPiMaxOccurs == Integer.MAX_VALUE
								|| typeGPiMaxOccurs == Integer.MAX_VALUE) {
							maxOccurs = Integer.MAX_VALUE;
						} else {
							maxOccurs = genPiMaxOccurs * typeGPiMaxOccurs;
						}
						copy.setCardinality(
								new Multiplicity(minOccurs, maxOccurs));
						propsToAdd.add(copy);
					}
					// remove the replaced property
					propsToRemove.add(relPi);
				}
			}

			// add new properties, if any, to inClass and model, ignoring
			// already existing ones
			genModel.add(propsToAdd, PropertyCopyDuplicatBehaviorIndicator.ADD);

			// remove properties of the current class which have been
			// processed from both the class and the model
			for (GenericPropertyInfo propToRemove : propsToRemove) {
				genModel.remove(propToRemove, false);
			}
		}

		/*
		 * remove processed unions that are no longer used by properties of the
		 * selected schemas; to do so, first identify which of the processed
		 * unions are still in use
		 */

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {
			if (processedUnionsById.containsKey(genPi.typeInfo().id)) {
				processedUnionsById.remove(genPi.typeInfo().id);
			}
		}

		for (GenericClassInfo unusedUnion : processedUnionsById.values()) {
			genModel.remove(unusedUnion);
		}
	}

	/**
	 *
	 * NOTE: removes all unions and potentially also data and object types (can
	 * be omitted via configuration parameter) from the model when finished,
	 * regardless whether these types are part of inheritance relationships;
	 * thus such relationships need to be flattened before this rule is
	 * executed.
	 *
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleFlattenTypes(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		/*
		 * Loop through all model properties; if the property of a feature type,
		 * object type, union, or data type has a type for which a type mapping
		 * exists, apply it - otherwise, if the type of the property is a data
		 * type, object type or union in the app schema, log that type (unless
		 * configuration parameters tell otherwise - which is covered by the
		 * method computTypesToProcessForFlattenTypes).
		 */

		// key: id of GenericClassInfo, value: GenericClassInfo
		TreeMap<String, GenericClassInfo> typesToProcessById = computeTypesToProcessForFlattenTypes(
				genModel, trfConfig);

		List<String> idsOfTypesToProcess = new ArrayList<String>();

		for (String key : typesToProcessById.keySet()) {
			idsOfTypesToProcess.add(key);
		}

		// ids of object types to process (relevant for cleaning up at the
		// end of processing)
		Set<String> idsOfObjectTypesToProcess = new HashSet<String>();

		// ids of data types to process (relevant for cleaning up at the
		// end of processing)
		Set<String> idsOfDataTypesToProcess = new HashSet<String>();

		for (GenericClassInfo typeCi : typesToProcessById.values()) {

			if (typeCi.category() == Options.OBJECT) {

				idsOfObjectTypesToProcess.add(typeCi.id());

			} else if (this.excludeDataTypePattern != null
					&& typeCi.category() == Options.DATATYPE) {

				/*
				 * NOTE: pattern matching was done in method
				 * computeTypesToProcessForFlattenTypes
				 */
				idsOfDataTypesToProcess.add(typeCi.id());
			}
		}

		// compute some general parameter values
		boolean includeUnionIdentifierTV = false;
		if (trfConfig.hasParameter(PARAM_INCLUDE_UNION_IDENTIFIER_TV)) {
			String tmp = trfConfig
					.getParameterValue(PARAM_INCLUDE_UNION_IDENTIFIER_TV);
			if (tmp.trim().equalsIgnoreCase("true")) {
				includeUnionIdentifierTV = true;
			}
		}

		boolean setMinCardinalityToZeroWhenMergingUnion = true;
		if (trfConfig.hasParameter(
				PARAM_SET_MIN_CARDINALITY_TO_ZERO_WHEN_MERGING_UNION)) {
			String tmp = trfConfig.getParameterValue(
					PARAM_SET_MIN_CARDINALITY_TO_ZERO_WHEN_MERGING_UNION);
			if (tmp.trim().equalsIgnoreCase("false")) {
				setMinCardinalityToZeroWhenMergingUnion = false;
			}
		}
		boolean mergeDescriptors = false;
		if (trfConfig.hasParameter(PARAM_MERGE_DESCRIPTORS)) {
			String tmp = trfConfig.getParameterValue(PARAM_MERGE_DESCRIPTORS);
			if (tmp.trim().equalsIgnoreCase("true")) {
				mergeDescriptors = true;
			}
		}

		/*
		 * Now process all app schema class properties with types that were
		 * tracked in the previous step for further processing:
		 *
		 * Copy the properties (as well as relevant dependencies and
		 * constraints) of these types into the classes that have properties
		 * that make use of them; then mark the type as processed so that we
		 * save the time to subsequently update its properties.
		 */

		// loop through all types that were identified for processing

		for (String idOfTypeToProcess : idsOfTypesToProcess) {

			GenericClassInfo typeToProcess = typesToProcessById
					.get(idOfTypeToProcess);

			/* Compute some information for the typeToProcess for later use. */

			// get TaggedValue omitWhenFlattened
			boolean omitWhenFlattened = false;
			String tvOmitWhenFlattened = typeToProcess
					.taggedValue("omitWhenFlattened");
			if (tvOmitWhenFlattened != null
					&& tvOmitWhenFlattened.trim().equalsIgnoreCase("true")) {
				omitWhenFlattened = true;
			}

			/*
			 * If the type from which all properties are copied down to the
			 * current class is a union, the separator to put between the name
			 * of the class property and the copied property shall be the value
			 * of separatorForPropertyFromUnion; otherwise it shall be the value
			 * of separatorForPropertyFromNonUnion
			 */
			String separator = separatorForPropertyFromNonUnion;
			if (typeToProcess.category() == Options.UNION) {
				separator = separatorForPropertyFromUnion;
			}

			/*
			 * Now loop through all app schema classes (and - in an inner loop -
			 * their properties)
			 */
			for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

				int categoryOfClass = genCi.category();

				/*
				 * If the class does not belong to one of the target types,
				 * directly continue with the next class.
				 */
				if (categoryOfClass != Options.FEATURE
						&& categoryOfClass != Options.OBJECT
						&& categoryOfClass != Options.UNION
						&& categoryOfClass != Options.DATATYPE
						&& categoryOfClass != Options.MIXIN) {

					continue;
				}

				List<GenericPropertyInfo> propsToAdd = new ArrayList<GenericPropertyInfo>();
				Set<GenericPropertyInfo> propsToRemove = new HashSet<GenericPropertyInfo>();

				/*
				 * Loop through all class properties and apply property copying
				 * to relevant properties (relevant = the type of the property
				 * was noted for property copying and equals the type that is
				 * currently under investigation [determined by the outer
				 * loop]).
				 */
				for (PropertyInfo pi : genCi.properties().values()) {

					// important: skip the property if it is not navigable
					if (!pi.isNavigable()) {
						continue;
					}

					/*
					 * NOTE for cast: the cast should be safe, because pi
					 * belongs to a GenericClassInfo (genCi)
					 */
					GenericPropertyInfo genPi = (GenericPropertyInfo) pi;

					Type type = genPi.typeInfo();

					if (typeToProcess.name().equals(type.name)) {

						/*
						 * isFlatTarget and resulting navigability now taken
						 * into account via
						 * RULE_TRF_PROP_REMOVE_NAVIGABILITY_BASEDON_ISFLATTARGET
						 */

						/*
						 * the property is navigable and type flattening would
						 * be applied for it; check if it represents a reflexive
						 * relationship and if those should be ignored
						 */
						if (ignoreReflexiveRelationshipInTypeFlattening
								&& representsReflexiveRelationship(genPi,
										genModel)) {

							/*
							 * properties that lead to a reflexive relationship
							 * are simply removed
							 */
							propsToRemove.add(genPi);

							result.addInfo(null, 20338, genPi.name(),
									genPi.inClass().name());

							continue;
						}

						/*
						 * The class property is of a type for which property
						 * copying will be applied. Thus at the end the property
						 * itself can be removed.
						 */
						propsToRemove.add(genPi);

						int seqNumIndex = 1;

						/*
						 * now copy all properties and relevant information of
						 * the type down into the current class
						 */
						for (PropertyInfo typePi : typeToProcess.properties()
								.values()) {

							/*
							 * NOTE for cast: the cast should be safe, because
							 * pi belongs to a GenericClassInfo (typeToProcess)
							 */
							GenericPropertyInfo typeGPi = (GenericPropertyInfo) typePi;

							String id = genPi.id() + "_replacedBy_"
									+ typeGPi.name();

							GenericPropertyInfo copy = typeGPi.createCopy(id);

							// merge global identifier information
							if (genCi.globalId() == null) {
								/*
								 * globalId from typeGPi can be used as-is,
								 * which is the default for the copy
								 */
							} else if (typeGPi.globalId() == null) {

								// use the global id from genPi
								copy.setGlobalId(genPi.globalId());

							} else {

								// merge global ids
								copy.setGlobalId(genPi.globalId() + "."
										+ typeGPi.globalId());
							}

							/* handle derived properties */
							if (genPi.isDerived()) {
								copy.setDerived(true);
							}

							/*
							 * ensure that the copy is not counted as an
							 * association role
							 */
							copy.setAttribute(true);
							copy.setAssociation(null);

							/*
							 * handle descriptors (except name and alias)
							 */
							if (mergeDescriptors) {
								copy.setDefinition(
										StringUtils.join(
												new String[] {
														genPi.definition(),
														copy.definition() },
												" "));
								copy.setDescription(
										StringUtils.join(
												new String[] {
														genPi.description(),
														copy.description() },
												" "));
								copy.setPrimaryCode(
										StringUtils.join(
												new String[] {
														genPi.primaryCode(),
														copy.primaryCode() },
												" "));
								// TBD: would it make sense to merge the
								// language()?
								copy.setLegalBasis(
										StringUtils.join(
												new String[] {
														genPi.legalBasis(),
														copy.legalBasis() },
												" "));
								copy.setDataCaptureStatements(ArrayUtils.addAll(
										genPi.dataCaptureStatements(),
										copy.dataCaptureStatements()));
								copy.setExamples(ArrayUtils.addAll(
										genPi.examples(), copy.examples()));
							} else {
								// (NOTE: for backwards compatibility after
								// mergeDescriptors has been introduced) Reset
								// the documentation for the copy if it is
								// empty.
								String s = copy.derivedDocumentation(
										"[[definition]][[description]]", "");
								if (s == null || s.length() == 0) {
									copy.setDefinition(genPi.definition());
									copy.setDescription(genPi.description());
									copy.setPrimaryCode(genPi.primaryCode());
									copy.setLanguage(genPi.language());
									copy.setLegalBasis(genPi.legalBasis());
									copy.setDataCaptureStatements(
											genPi.dataCaptureStatements());
									copy.setExamples(genPi.examples());
								}
							}

							/*
							 * Combine the property names unless this shall be
							 * omitted (determined by tagged value on type to
							 * process). In that case, remove the last component
							 * in the name path (following the last occurrence
							 * of the value of separatorForPropertyFromNonUnion
							 * in the name)
							 */
							String piName = pi.name();
							String piCode = getCode(pi);
							String typeGPiName = typeGPi.name();
							String typeGPiCode = getCode(typeGPi);
							String newCode = null;
							String newName;

							if (omitWhenFlattened) {
								if (piName.contains(
										separatorForPropertyFromNonUnion)) {
									newName = piName.substring(0,
											piName.lastIndexOf(
													separatorForPropertyFromNonUnion))
											+ separator + typeGPiName;
								} else {
									newName = typeGPiName;
								}
							} else {
								newName = piName + separator + typeGPiName;
							}
							copy.setName(newName);

							if (hasCode(pi)) {

								if (hasCode(typeGPi)) {

									if (omitWhenFlattened) {
										if (piCode.contains(
												separatorForPropertyFromNonUnion)) {
											newCode = piCode.substring(0,
													piCode.lastIndexOf(
															separatorForPropertyFromNonUnion))
													+ separator + typeGPiCode;
										} else {
											newCode = typeGPiCode;
										}
									} else {
										newCode = piCode + separator
												+ typeGPiCode;
									}
								} else {

									/*
									 * We only have code for pi and need to use
									 * typeGPi name to construct the code of the
									 * copy.
									 */
									if (omitWhenFlattened) {
										if (piCode.contains(
												separatorForPropertyFromNonUnion)) {
											newCode = piCode.substring(0,
													piCode.lastIndexOf(
															separatorForPropertyFromNonUnion))
													+ separator + typeGPiName;
										} else {
											newCode = typeGPiName;
										}
									} else {
										newCode = piCode + separator
												+ typeGPiName;
									}
								}
							} else {

								if (hasCode(typeGPi)) {

									/*
									 * We do not have a code for pi but for
									 * typeGPi.
									 */
									if (omitWhenFlattened) {
										if (piName.contains(
												separatorForPropertyFromNonUnion)) {
											newCode = piName.substring(0,
													piName.lastIndexOf(
															separatorForPropertyFromNonUnion))
													+ separator + typeGPiCode;
										} else {
											newCode = typeGPiCode;
										}
									} else {
										newCode = piName + separator
												+ typeGPiCode;
									}
								} else {

									/*
									 * We neither have a code for pi nor for
									 * typeGPi and thus need to use their names
									 * to construct the code value.
									 */
									if (omitWhenFlattened) {
										if (piName.contains(
												separatorForPropertyFromNonUnion)) {
											newCode = piName.substring(0,
													piName.lastIndexOf(
															separatorForPropertyFromNonUnion))
													+ separator + typeGPiName;
										} else {
											newCode = typeGPiName;
										}
									} else {
										newCode = piName + separator
												+ typeGPiName;
									}
								}
							}

							setCode(copy, newCode);

							// merge the "name" tagged values if they exist
							String piNameTV = pi.taggedValue("name");
							String typeGPiNameTV = typeGPi.taggedValue("name");

							if (piNameTV != null
									&& piNameTV.trim().length() > 0) {

								String newNameTV = piNameTV;

								if (typeGPiNameTV != null
										&& typeGPiNameTV.trim().length() > 0) {
									// we need to merge the 'name' tagged values
									newNameTV = piNameTV + " - "
											+ typeGPiNameTV;
								} else {
									// typeGPi does not have the 'name'
									// tagged value but pi does
								}

								copy.setTaggedValue("name", newNameTV, false);

							} else {
								/*
								 * so pi does not contain the 'name' tagged
								 * value; then there is no need to update the
								 * tagged values of the typeGPi copy
								 */
							}

							/*
							 * 2014-07-08: this would also be the place to
							 * process any profile information. At the moment
							 * there is no rule to determine how profiling
							 * information should be handled by the Flattener.
							 * Thus profiling must be applied before flattening.
							 */

							copy.setInClass(genCi);

							if (includeUnionIdentifierTV) {

								if (genPi.cardinality().maxOccurs > 1) {
									TaggedValues tvs = copy.taggedValuesAll();
									tvs.remove(UNION_SET_TAG_NAME);
									copy.setTaggedValues(tvs, false);
								} else if (typeToProcess
										.category() == Options.UNION
										&& genCi.category() == Options.UNION) {
									// nothing to do
								} else if (genCi.category() == Options.UNION) {
									/*
									 * remove potentially existing union
									 * identifier tag from the copy
									 */
									TaggedValues tvs = copy.taggedValuesAll();
									tvs.remove(UNION_SET_TAG_NAME);
									copy.setTaggedValues(tvs, false);
								} else if (typeToProcess
										.category() == Options.UNION) {
									/*
									 * set union identifier tag in the copy;
									 * handle the case where a union was used in
									 * a union
									 */
									TaggedValues tvs = copy.taggedValuesAll();

									String setName;
									int lastIndexOfUnionSeparator = pi.name()
											.lastIndexOf(
													separatorForPropertyFromUnion);
									int lastIndexOfNonUnionSeparator = pi.name()
											.lastIndexOf(
													separatorForPropertyFromNonUnion);

									if (lastIndexOfUnionSeparator > lastIndexOfNonUnionSeparator) {
										// last merge was a union property, just
										// like now
										setName = pi.name().substring(0,
												lastIndexOfUnionSeparator);
									} else {
										setName = pi.name();
									}

									tvs.put(UNION_SET_TAG_NAME, setName);
									copy.setTaggedValues(tvs, false);
								} else if (copy.taggedValue(
										UNION_SET_TAG_NAME) != null) {
									/*
									 * extend union identifier tag in the copy
									 */
									TaggedValues tvs = copy.taggedValuesAll();
									String tmp = tvs
											.getFirstValue(UNION_SET_TAG_NAME);
									tvs.put(UNION_SET_TAG_NAME,
											pi.name() + separator + tmp);
									copy.setTaggedValues(tvs, false);
								}
							}

							/*
							 * ensure that "sequenceNumber" tagged value is also
							 * updated
							 */
							copy.setSequenceNumber(pi.sequenceNumber()
									.createCopyWithSuffix(seqNumIndex), true);
							seqNumIndex++;

							int minOccurs;

							if (typeToProcess.category() == Options.UNION
									&& (setMinCardinalityToZeroWhenMergingUnion
											|| pi.cardinality().maxOccurs > 1)) {
								minOccurs = 0;
							} else {
								minOccurs = pi.cardinality().minOccurs
										* typeGPi.cardinality().minOccurs;
							}

							int piMaxOccurs = pi.cardinality().maxOccurs;
							int typeGPiMaxOccurs = typeGPi
									.cardinality().maxOccurs;
							int maxOccurs = 0;
							if (piMaxOccurs == Integer.MAX_VALUE
									|| typeGPiMaxOccurs == Integer.MAX_VALUE) {
								maxOccurs = Integer.MAX_VALUE;
							} else {
								maxOccurs = piMaxOccurs * typeGPiMaxOccurs;
							}
							copy.setCardinality(
									new Multiplicity(minOccurs, maxOccurs));

							propsToAdd.add(copy);

						}

						/*
						 * Now copy the constraints over as well, so that they
						 * are not lost.
						 */
						genCi.addConstraints(typeToProcess.constraints());

						// TODO addition of OCL-constraint to define choice
						// between properties copied from a union type

						// TODO copy dependencies from the type that is being
						// processed

					}
				}

				// add new properties, if any, to inClass and model, ignoring
				// already existing ones
				genModel.add(propsToAdd,
						PropertyCopyDuplicatBehaviorIndicator.IGNORE);

				// remove properties of the current class which have been
				// processed from both the class and the model
				for (GenericPropertyInfo propToRemove : propsToRemove) {
					genModel.remove(propToRemove, false);
				}
			}

			/*
			 * IMPORTANT FOR FLATTENING LARGE MODELS:
			 *
			 * Remove references to typeToProcess now - completely, i.e. both
			 * from the model and also in this method, so that the GC can
			 * collect it.
			 */
			genModel.remove(typeToProcess);
			typesToProcessById.remove(idOfTypeToProcess);

		}

		/*
		 * TODO - remove all datatypes, unions and object types that may not
		 * have been used as types in properties of selected schemas, but that
		 * are still owned by packages in the selected schemas
		 */

		/*
		 * Now that all data types, object types and unions should have either
		 * been flattened or replaced (via type mappings) in the app schema,
		 * remove them from the model. Remove object types if flattenObjectTypes
		 * is true or if they match the inclusion regex. Remove data types
		 * unless they have been excluded (via configuration parameter).
		 */
		if (this.excludeDataTypePattern != null) {

			/*
			 * TODO this does not remove data types that were not used by
			 * properties of selected schema classes; to achieve this, we need
			 * to check each remaining data type in selected schemas and if its
			 * name matches the regular expression, remove it.
			 */
			for (String id : idsOfDataTypesToProcess) {
				genModel.removeByClassId(id);
			}

		} else {

			// remove all complex data types
			genModel.removeByClassCategory(Options.DATATYPE);
		}

		if (flattenObjectTypes) {

			// fine, we can just go ahead and remove all object types
			genModel.removeByClassCategory(Options.OBJECT);

		} else if (includeObjectTypeRegex != null) {

			// alright, let's see if we still had some object types to remove
			// that were included in the flattening via the
			// PARAM_FLATEN_OBJECT_TYPES_INCLUDE_REGEX

			/*
			 * TODO this does not remove object types that were not used by
			 * properties of selected schema classes; to achieve this, we need
			 * to check each remaining object type in selected schemas and if
			 * its name matches the regular expression, remove it.
			 */
			for (String id : idsOfObjectTypesToProcess) {
				genModel.removeByClassId(id);
			}
		}

		genModel.removeByClassCategory(Options.UNION);
	}

	/**
	 * @param genPi
	 * @param genModel
	 * @return <code>true</code> if the inClass of genPi is kind of the type
	 *         that is the value type of genPi, else <code>false</code>
	 */
	private boolean representsReflexiveRelationship(GenericPropertyInfo genPi,
			GenericModel genModel) {

		String typeId = genPi.typeInfo().id;

		ClassInfo typeCi = genModel.classById(typeId);

		if (typeCi == null) {
			return false;
		} else {
			return genModel.isKindOf(genPi.inClass(), typeCi);
		}
	}

	/**
	 * @param genModel
	 * @param trfConfig
	 * @return a map with types to be processed in flatten types rule; can be
	 *         empty but not <code>null</code>
	 */
	private TreeMap<String, GenericClassInfo> computeTypesToProcessForFlattenTypes(
			GenericModel genModel, TransformerConfiguration trfConfig) {

		TreeMap<String, GenericClassInfo> typesToProcessById = new TreeMap<String, GenericClassInfo>();

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			int categoryOfInClass = genPi.inClass().category();
			if (categoryOfInClass != Options.FEATURE
					&& categoryOfInClass != Options.OBJECT
					&& categoryOfInClass != Options.UNION
					&& categoryOfInClass != Options.DATATYPE) {

				/*
				 * The property does not belong to one of the target types, thus
				 * directly continue with the next property.
				 */
				continue;
			}

			Type type = genPi.typeInfo();

			/*
			 * If a type mapping exists for the type of a property that is in
			 * the target list for type processing, switch it. Otherwise,
			 * determine if we need to keep track of the type of the property
			 * for subsequent processing.
			 *
			 * Type mapping should be performed in any case.
			 */
			if (trfConfig.hasMappingForType(RULE_TRF_PROP_FLATTEN_TYPES,
					type.name)) {

				// update the type of the property to the target type

				// TBD: check type update against rule specific type mappings
				ClassInfo targetCi = genModel.classByName(
						trfConfig.getMappingForType(RULE_TRF_PROP_FLATTEN_TYPES,
								type.name).getTargetType());

				if (targetCi == null) {

					String targetTypeName = trfConfig
							.getMappingForType(RULE_TRF_PROP_FLATTEN_TYPES,
									type.name)
							.getTargetType();

					result.addDebug(null, 20318, targetTypeName, genPi.name(),
							genPi.inClass().name());

					type.id = UNKNOWN;
					type.name = targetTypeName;
				} else {
					type.id = targetCi.id();
					type.name = targetCi.name();
				}

			} else {

				if (type.id.equals(UNKNOWN)) {

					/*
					 * Then we cannot flatten the type. For a type.id to be
					 * 'unknown' the reason why should have already been
					 * reported.
					 */

				} else {

					ClassInfo typeCi = genModel.classById(type.id);

					if (typeCi == null) {

						// We cannot flatten the type if it is not available.
						//
						// /*
						// * We can replace the type, however, if a type mapping
						// exists
						// * for it in the configuration.
						// */
						// if
						// (trfConfig.hasMappingForType(RULE_TRF_PROP_FLATTEN_TYPES,
						// type.name)) {
						//
						// // update the type of the property to the target type
						// String targetTypeName = trfConfig
						// .getMappingForType(RULE_TRF_PROP_FLATTEN_TYPES,
						// type.name)
						// .getTargetType();
						//
						// MessageContext mc = result.addWarning(null, 20302,
						// targetTypeName, type.name);
						// if (mc != null)
						// mc.addDetail(null, 20308, "Property",
						// genPi.fullName());
						//
						// type.id = UNKNOWN;
						// type.name = targetTypeName;
						//
						// } else {
						MessageContext mc = result.addDebug(null, 20301,
								genPi.inClass().name() + "." + genPi.name(),
								type.name);
						if (mc != null)
							mc.addDetail(null, 20308, "Property",
									genPi.fullName());

						type.id = UNKNOWN;
						// }
						// continue;
					} else {

						/*
						 * Check category of the type of the property based upon
						 * the desired processing (unions, datatypes [unless
						 * excluded] and types [depending upon the value of the
						 * flattenObjectTypes parameter]). If the type category
						 * does not belong to one targeted for processing,
						 * directly continue with the next property.
						 */
						boolean processType = false;

						if (typeCi.category() == Options.UNION) {

							processType = true;

						} else if (typeCi.category() == Options.DATATYPE) {

							if (excludeDataTypeRegex != null) {

								Matcher m = excludeDataTypePattern
										.matcher(typeCi.name());

								if (m.matches()) {
									processType = false;
									result.addDebug(null, 20344, typeCi.name(), excludeDataTypeRegex, PARAM_FLATTEN_DATATYPES_EXCLUDE_REGEX);
								} else {
									processType = true;
									result.addDebug(null, 20345, typeCi.name(), excludeDataTypeRegex, PARAM_FLATTEN_DATATYPES_EXCLUDE_REGEX);
								}

							} else {
								processType = true;
							}

						} else if (typeCi.category() == Options.OBJECT) {

							if (flattenObjectTypes) {

								processType = true;

							} else if (includeObjectTypeRegex != null) {

								Matcher m = includeObjectTypePattern
										.matcher(typeCi.name());

								if (m.matches()) {
									processType = true;
									result.addDebug(null, 20344, typeCi.name(), includeObjectTypeRegex, PARAM_FLATTEN_OBJECT_TYPES_INCLUDE_REGEX);
								} else {
									processType = false;
									result.addDebug(null, 20345, typeCi.name(), includeObjectTypeRegex, PARAM_FLATTEN_OBJECT_TYPES_INCLUDE_REGEX);
								}

							} else {
								processType = false;
							}
						}

						if (processType) {

							/*
							 * TBD: only if the type of the property is in the
							 * app schema, note it for further processing
							 */
							if (genModel.isInAppSchema(typeCi)) {

								/*
								 * NOTE for cast: the cast should be safe,
								 * because typeCi belongs to an application
								 * schema selected for processing (the contents
								 * of which are parsed to the generic types)
								 */
								typesToProcessById.put(typeCi.id(),
										(GenericClassInfo) typeCi);
							}
						}
					}
				}
			}
		}

		return typesToProcessById;
	}

	/**
	 * Identifies reflexive and circular dependencies in and between types that
	 * will be flattened during execution of
	 * {@value #RULE_TRF_PROP_FLATTEN_TYPES}.
	 *
	 * @param typesToProcessById
	 */
	private void identifyCircularDependencies(
			Map<String, GenericClassInfo> typesToProcessById) {

		result.addInfo(null, 20329);

		DirectedGraph<String, PropertySetEdge> graph = new DirectedMultigraph<String, PropertySetEdge>(
				new ClassBasedEdgeFactory<String, PropertySetEdge>(
						PropertySetEdge.class));

		// establish graph vertices
		for (GenericClassInfo typeToProcess : typesToProcessById.values()) {
			graph.addVertex(
					typeToProcess.pkg().name() + "::" + typeToProcess.name());
		}

		// establish edges

		/*
		 * key: name of type with reflexive relationship(s); value: properties
		 * that cause the reflexive relationship(s)
		 */
		Map<String, Set<String>> refTypeInfo = new TreeMap<String, Set<String>>();

		for (GenericClassInfo typeToProcess : typesToProcessById.values()) {

			String typeToProcessKey = typeToProcess.pkg().name() + "::"
					+ typeToProcess.name();

			/*
			 * key: {value type package name}::{value type name}, value: names
			 * of properties of typeToProcess that have that value type
			 */
			Map<String, Set<String>> propertiesByValueTypeName = new HashMap<String, Set<String>>();

			for (PropertyInfo pi : typeToProcess.properties().values()) {

				// skip the property if it is not navigable or if its value
				// type is not in the collection of types to process
				if (!pi.isNavigable()
						|| !typesToProcessById.containsKey(pi.typeInfo().id)) {
					continue;
				}

				/*
				 * whether or not a property truly is navigable may depend on
				 * other information, like the tagged value isFlatTarget; such
				 * information should have been used to remove non-navigable
				 * properties before circle detection is performed
				 */

				/*
				 * The class property is of a type for which property copying
				 * will be applied, keep track of this.
				 */
				GenericClassInfo targetType = typesToProcessById
						.get(pi.typeInfo().id);

				String key = targetType.pkg().name() + "::" + targetType.name();
				Set<String> props;
				if (propertiesByValueTypeName.containsKey(key)) {
					props = propertiesByValueTypeName.get(key);
				} else {
					props = new TreeSet<String>();
					propertiesByValueTypeName.put(key, props);
				}
				props.add(pi.name());
				// }
			}

			// create directed edges and thereby identify reflexive
			// relationships
			for (String targetKey : propertiesByValueTypeName.keySet()) {

				Set<String> props = propertiesByValueTypeName.get(targetKey);

				if (typeToProcessKey.equals(targetKey)) {
					/*
					 * loops are not supported in cycle detection of JGraphT,
					 * thus log infos to create a warning
					 */
					refTypeInfo.put(typeToProcessKey, props);

				} else {

					graph.addEdge(typeToProcessKey, targetKey,
							new PropertySetEdge(typeToProcessKey, targetKey,
									props));
				}
			}
		}

		/*
		 * Log occurrence of reflexive relationships.
		 */
		if (refTypeInfo.isEmpty()) {
			result.addInfo(null, 20331);
		} else {
			for (String key : refTypeInfo.keySet()) {
				result.addInfo(null, 20330, key,
						join(refTypeInfo.get(key), ","));
			}
		}

		/*
		 * 2015-01-19 JE: these are alternative algorithms for cycle detection;
		 * not sure which one is the best, so I just picked one.
		 */

		// DirectedSimpleCycles<String, PropertySetEdge<String>> alg = new
		// JohnsonSimpleCycles<String, PropertySetEdge<String>>(
		// graph);

		// DirectedSimpleCycles<String, PropertySetEdge<String>> alg = new
		// SzwarcfiterLauerSimpleCycles<String, PropertySetEdge<String>>(
		// graph);

		// DirectedSimpleCycles<String, PropertySetEdge<String>> alg = new
		// TarjanSimpleCycles<String, PropertySetEdge<String>>(graph);

		DirectedSimpleCycles<String, PropertySetEdge> alg = new TiernanSimpleCycles<String, PropertySetEdge>(
				graph);

		List<List<String>> cycles = alg.findSimpleCycles();

		if (cycles != null && cycles.size() > 0) {

			for (List<String> cycle : cycles) {

				result.addInfo(null, 20326);

				for (int i = 0; i < cycle.size(); i++) {

					String source, target;

					if (alg instanceof JohnsonSimpleCycles<?, ?>) {

						source = cycle.get(i);

						if (i == 0) {
							target = cycle.get(cycle.size() - 1);
						} else {
							target = cycle.get(i - 1);
						}

					} else if (alg instanceof SzwarcfiterLauerSimpleCycles<?, ?>) {

						source = cycle.get(i);

						if (i == cycle.size() - 1) {
							target = cycle.get(0);
						} else {
							target = cycle.get(i + 1);
						}

					} else if (alg instanceof TarjanSimpleCycles<?, ?>) {

						source = cycle.get(i);

						if (i == cycle.size() - 1) {
							target = cycle.get(0);
						} else {
							target = cycle.get(i + 1);
						}

					} else {

						// alg instanceof TiernanSimpleCycles

						source = cycle.get(i);

						if (i == cycle.size() - 1) {
							target = cycle.get(0);
						} else {
							target = cycle.get(i + 1);
						}
					}

					PropertySetEdge edge = graph.getEdge(source, target);

					result.addInfo(null, 20327, source, target,
							edge.toString());
				}
			}
		} else {
			result.addInfo(null, 20328);
		}

		alg = null;
		graph = null;
	}

	/**
	 * Associations with at least one end having cardinality >1 are dissolved.
	 *
	 * @param genModel
	 * @param options
	 * @param trfConfig
	 * @param result
	 */
	private void applyRuleMultiplicity(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		boolean applyMaxMultThreshold = false;
		int maxMultiplicityThreshold = -1;
		if (rules.contains(
				RULE_TRF_PROP_FLATTEN_MULTIPLICITY_WITHMAXMULTTHRESHOLD)) {

			String thresholdValue = trfConfig
					.getParameterValue(PARAM_MAX_MULTIPLICITY_THRESHOLD);

			boolean isInvalidThresholdValue = false;

			if (thresholdValue != null && thresholdValue.trim().length() > 0) {

				try {

					int mmt = Integer.parseInt(thresholdValue);
					if (mmt <= 1) {
						isInvalidThresholdValue = true;
					} else {
						applyMaxMultThreshold = true;
						maxMultiplicityThreshold = mmt;
					}

				} catch (NumberFormatException e) {
					isInvalidThresholdValue = true;
				}

			} else {
				isInvalidThresholdValue = true;
			}

			if (isInvalidThresholdValue) {
				result.addError(null, 20341,
						RULE_TRF_PROP_FLATTEN_MULTIPLICITY_WITHMAXMULTTHRESHOLD,
						PARAM_MAX_MULTIPLICITY_THRESHOLD);
			}
		}

		boolean keepBiDirectionalAssociations = false;
		if (rules.contains(
				RULE_TRF_PROP_FLATTEN_MULTIPLICITY_KEEPBIDIRECTIONALASSOCIATIONS)) {
			keepBiDirectionalAssociations = true;
		}

		ShapeChangeResult result = genModel.result();

		// get default maxOccurs from configuration
		int maxOccursGlobal = 3;
		String maxOccParam = trfConfig.getParameterValue(PARAM_MAXOCCURS);
		if (maxOccParam != null && maxOccParam.trim().length() > 0) {
			maxOccursGlobal = Integer.parseInt(maxOccParam);
			if (maxOccursGlobal < 1) {
				result.addWarning(null, 20304, "" + maxOccursGlobal);
				maxOccursGlobal = 3;
			}
		}

		Map<String, Integer> specificMaxOccursByIdPattern = new HashMap<String, Integer>();

		String[] specificMaxOccurs = trfConfig
				.getListParameterValue(PARAM_MAXOCCURS_FOR_SPECIFIC_PROPERTIES);
		if (specificMaxOccurs != null && specificMaxOccurs.length > 0) {
			for (String smo : specificMaxOccurs) {
				String[] smoParts = smo.split("::");
				if (smoParts.length != 3 || smoParts[0].length() == 0
						|| smoParts[1].length() == 0) {
					result.addError(null, 20310,
							PARAM_MAXOCCURS_FOR_SPECIFIC_PROPERTIES, smo);
				} else {
					try {
						Integer i = new Integer(smoParts[2]);
						specificMaxOccursByIdPattern
								.put(smoParts[0] + "::" + smoParts[1], i);
					} catch (NumberFormatException e) {
						result.addError(null, 20310,
								PARAM_MAXOCCURS_FOR_SPECIFIC_PROPERTIES, smo);
					}
				}
			}
		}

		String separatorForPropertyIndexNumberParam = trfConfig
				.getParameterValue(
						TRANSFORMER_SEPARATOR_FOR_PROPERTY_INDEX_NUMBER);
		if (separatorForPropertyIndexNumberParam != null) {

			// Note that a length of 0 IS allowed
			separatorForPropertyIndexNumber = separatorForPropertyIndexNumberParam;
		}

		boolean ignoreFeatureOrObjectTypedProperties = false;

		if (trfConfig.hasParameter(
				PARAM_IGNORE_FEATURE_OR_OBJECT_TYPED_PROPERTIES)) {
			String b = trfConfig.getParameterValue(
					PARAM_IGNORE_FEATURE_OR_OBJECT_TYPED_PROPERTIES);
			if (b.trim().equalsIgnoreCase("true")) {
				ignoreFeatureOrObjectTypedProperties = true;
			}
		}

		boolean ignoreFeatureTypedProperties = false;

		if (trfConfig.hasParameter(PARAM_IGNORE_FEATURE_TYPED_PROPERTIES)) {
			String b = trfConfig
					.getParameterValue(PARAM_IGNORE_FEATURE_TYPED_PROPERTIES);
			if (b.trim().equalsIgnoreCase("true")) {
				ignoreFeatureTypedProperties = true;
			}
		}

		/*
		 * dissolve any association where one of its navigable ends has
		 * cardinality >1 (basically turn navigable association ends into
		 * attributes), depending on desired behavior (max multiplicity
		 * threshold must be taken into account, as well as behavior to keep
		 * bi-directional associations)
		 */

		List<GenericAssociationInfo> associationsToDissolve = new ArrayList<GenericAssociationInfo>();

		for (GenericAssociationInfo genAI : genModel
				.selectedSchemaAssociations()) {

			PropertyInfo end1 = genAI.end1();
			PropertyInfo end2 = genAI.end2();

			if (end1.inClass() != null && genModel.isInAppSchema(end1.inClass())
					&& end2.inClass() != null
					&& genModel.isInAppSchema(end2.inClass())
					&& ((ignoreFeatureTypedProperties
							&& end1.categoryOfValue() == Options.FEATURE
							&& end2.categoryOfValue() == Options.FEATURE)
							|| (ignoreFeatureOrObjectTypedProperties
									&& (end1.categoryOfValue() == Options.FEATURE
											|| end1.categoryOfValue() == Options.GMLOBJECT
											|| end1.categoryOfValue() == Options.OBJECT
											|| end1.categoryOfValue() == Options.MIXIN)
									&& (end2.categoryOfValue() == Options.FEATURE
											|| end2.categoryOfValue() == Options.GMLOBJECT
											|| end2.categoryOfValue() == Options.OBJECT
											|| end2.categoryOfValue() == Options.MIXIN))

					)) {
				// alright, then we keep this association as is
				continue;
			}

			/*
			 * Determine if an association end shall be flattened: it must be
			 * navigable, have a maxOccurs > 1, and not have maxOccurs > the
			 * threshold if applyMaxMultiplicity flattening behavior is enabled
			 */
			boolean end1ShallBeFlattened = false;
			boolean end2ShallBeFlattened = false;

			if (end1 != null && end1.isNavigable()
					&& end1.cardinality().maxOccurs > 1
					&& !(applyMaxMultThreshold && end1
							.cardinality().maxOccurs > maxMultiplicityThreshold)) {
				end1ShallBeFlattened = true;
			}

			if (end2 != null && end2.isNavigable()
					&& end2.cardinality().maxOccurs > 1
					&& !(applyMaxMultThreshold && end2
							.cardinality().maxOccurs > maxMultiplicityThreshold)) {
				end2ShallBeFlattened = true;
			}

			if (end1ShallBeFlattened || end2ShallBeFlattened) {

				if (keepBiDirectionalAssociations && end1.isNavigable()
						&& end2.isNavigable()) {
					/*
					 * The association is bi-directional and one or both ends
					 * would be flattened. Because bi-directional n:m
					 * associations shall be kept we issue a warning and don't
					 * dissolve the association.
					 */
					result.addWarning(null, 20342, end1.inClass().name(),
							end1.name(), end2.inClass().name(), end2.name());

				} else {

					associationsToDissolve.add(genAI);
				}
			}
		}

		for (GenericAssociationInfo genAI : associationsToDissolve) {
			genModel.dissolveAssociation(genAI);
		}

		// now flatten multiple properties
		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			SortedMap<StructuredNumber, PropertyInfo> properties = genCi
					.properties();

			if (properties == null || properties.isEmpty())
				continue;

			List<GenericPropertyInfo> propsToAdd = new ArrayList<GenericPropertyInfo>();
			List<GenericPropertyInfo> propsToRemove = new ArrayList<GenericPropertyInfo>();

			for (PropertyInfo pi : properties.values()) {

				// =============================
				/*
				 * check if multiplicity flattening shall really be applied to
				 * the property
				 */
				// =============================

				// lookup type of the property
				Type piType = pi.typeInfo();
				ClassInfo typeCi = genModel.classById(piType.id);

				if (((ignoreFeatureTypedProperties
						&& pi.categoryOfValue() == Options.FEATURE)
						|| (ignoreFeatureOrObjectTypedProperties
								&& (pi.categoryOfValue() == Options.FEATURE
										|| pi.categoryOfValue() == Options.GMLOBJECT
										|| pi.categoryOfValue() == Options.OBJECT
										|| pi.categoryOfValue() == Options.MIXIN)))
						&& typeCi != null && genModel.isInAppSchema(typeCi)) {
					/*
					 * type of the property is part of the application schema
					 * and a type that should be ignored -> we keep this
					 * property as is
					 */
					continue;
				}

				/*
				 * NOTE for cast: the cast should be safe, because pi belongs to
				 * a GenericClassInfo (genCi)
				 */
				GenericPropertyInfo genPi = (GenericPropertyInfo) pi;

				if (genPi.cardinality().maxOccurs <= 1)
					continue;

				/*
				 * If maxOccurs of the property is greater than the threshold
				 * for which multiplicity flattening shall be performed
				 * (behavior must be enabled), keep the property as-is and
				 * continue.
				 */
				if (applyMaxMultThreshold && genPi
						.cardinality().maxOccurs > maxMultiplicityThreshold) {
					continue;
				}

				/*
				 * If we have a property of a bi-directional association that
				 * shall be kept as-is, we continue.
				 */
				if (!genPi.isAttribute() && keepBiDirectionalAssociations
						&& genPi.reverseProperty() != null
						&& genPi.reverseProperty().isNavigable()) {
					continue;
				}

				// =============================
				/*
				 * Apply multiplicity flattening.
				 */
				// =============================

				// determine maxOccurs from configuration and tagged value
				int maxOccurs = maxOccursGlobal;
				String piMaxOccTaggedValue = genPi.taggedValue(PARAM_MAXOCCURS);

				if (piMaxOccTaggedValue != null
						&& piMaxOccTaggedValue.trim().length() > 0) {

					int piMaxOcc = Integer.parseInt(piMaxOccTaggedValue);
					if (piMaxOcc < 1) {
						result.addWarning(null, 20305, pi.name(), genCi.name(),
								"" + piMaxOcc, "" + maxOccursGlobal);

					} else {
						maxOccurs = piMaxOcc;
					}
				}

				String specMaxOccursId = pi.inClass().name() + "::" + pi.name();
				if (specificMaxOccursByIdPattern.containsKey(specMaxOccursId)) {
					maxOccurs = specificMaxOccursByIdPattern
							.get(specMaxOccursId);
				}

				// ensure that maxOccurs is not greater than the maximum
				// multiplicity defined for the property
				if (maxOccurs > genPi.cardinality().maxOccurs) {
					maxOccurs = genPi.cardinality().maxOccurs;
				}

				// create copies of the property and add them to the class /
				// model
				StructuredNumber genPiSeqNum = genPi.sequenceNumber();

				for (int i = 1; i <= maxOccurs; i++) {

					String newId = genPi.id() + i;
					String newName = genPi.name()
							+ separatorForPropertyIndexNumber + i;

					GenericPropertyInfo copy = genPi.createCopy(newId);
					copy.setName(newName);

					if (hasCode(genPi)) {
						setCode(copy, getCode(genPi)
								+ separatorForPropertyIndexNumber + i);
					}

					Multiplicity card = new Multiplicity();
					if (genPi.cardinality().minOccurs != 0) {
						if (i <= genPi.cardinality().minOccurs) {
							card.minOccurs = 1;
						} else {
							card.minOccurs = 0;
						}
					} else {
						card.minOccurs = 0;
					}
					card.maxOccurs = 1;
					copy.setCardinality(card);

					/*
					 * ensure that "sequenceNumber" tagged value is also updated
					 */
					copy.setSequenceNumber(genPiSeqNum.createCopyWithSuffix(i),
							true);

					if (options.isLoadGlobalIdentifiers()) {
						String newGlobalId = genPi.globalId()
								.concat("[" + i + "]");
						copy.setGlobalId(newGlobalId);
					}

					propsToAdd.add(copy);
					propsToRemove.add(genPi);
				}
			}

			// add new property to model (TODO: currently also
			// automatically adds the property to the class
			for (GenericPropertyInfo piToAdd : propsToAdd) {
				// TODO fine-tune duplicate property behavior?
				genModel.add(piToAdd, genCi);
			}

			// remove old properties with cardinality > 1 in class and in model
			for (GenericPropertyInfo piToRemove : propsToRemove) {
				genModel.remove(piToRemove, false);
			}
		}

	}

	/**
	 *
	 * TODO: realize flattening of inheritance structures from external packages
	 *
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleInheritance(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		// key: class id, value: class
		Map<String, GenericClassInfo> genSuperclassesById = new HashMap<String, GenericClassInfo>();
		Map<String, GenericClassInfo> genLeafclassesById = new HashMap<String, GenericClassInfo>();

		Map<String, GenericClassInfo> genSuperclassUnionsByName = new TreeMap<String, GenericClassInfo>();

		Pattern inclusionPattern = null;

		if (trfConfig.hasParameter(PARAM_INHERITANCE_INCLUDE_REGEX)) {
			String inclusionRegex = trfConfig
					.getParameterValue(PARAM_INHERITANCE_INCLUDE_REGEX);

			inclusionPattern = Pattern.compile(inclusionRegex);
		}

		boolean addAttributesAtBottom = trfConfig
				.hasRule(RULE_TRF_CLS_FLATTEN_INHERITANCE_ADD_ATTRIBUTES_AT_BOTTOM);

		/*
		 * Identify supertypes and leafs in selected schemas. A class is only
		 * identified as a supertype if it has at least one subtype from within
		 * the selected schemas; otherwise treat it as a leaf class.
		 */
		for (GenericClassInfo genCls : genModel.selectedSchemaClasses()) {

			if (genCls.subtypes() != null && genCls.subtypes().size() > 0
					&& !allSubtypesOutsideSelectedSchemas(genCls)) {

				boolean includeSupertype = true;

				/*
				 * genCls is a supertype - only note this class for further
				 * processing if it - or one of its supertypes (recursively up
				 * in the derivation hierarchy, but staying in the application
				 * schema selected for processing) matches the inclusion regex
				 */
				if (inclusionPattern != null) {

					includeSupertype = matchesRegexInSupertypeHierarchy(genCls,
							inclusionPattern);
				}

				if (includeSupertype) {
					genSuperclassesById.put(genCls.id(), genCls);
				}

			} else {

				/*
				 * so apparently genCls is not a supertype (does not have a
				 * subtype that is contained in selected schemas) -> check if
				 * the class has a supertype that is in the app schema(s) and -
				 * if set via parameter - fulfills the inclusion criteria; if so
				 * keep track of it for later reference
				 */
				if (genCls.supertypes() != null
						&& genCls.supertypes().size() > 0) {

					boolean allSupertypesOutsideAppSchema = true;

					for (String supertypeId : genCls.supertypes()) {
						if (genModel.isInAppSchema(
								genModel.classById(supertypeId))) {
							allSupertypesOutsideAppSchema = false;
							break;
						}
					}

					if (!allSupertypesOutsideAppSchema) {

						boolean include = true;

						if (inclusionPattern != null) {
							include = matchesRegexInSupertypeHierarchy(genCls,
									inclusionPattern);
						}

						if (include) {
							genLeafclassesById.put(genCls.id(), genCls);
						}
					}
				}
			}
		}

		Set<String> idsOfUnprocessedSupertypes = new HashSet<String>();
		for (String superclassId : genSuperclassesById.keySet()) {
			idsOfUnprocessedSupertypes.add(superclassId);
		}

		/*
		 * We want to copy the content of all selected supertypes down to their
		 * subtypes, starting at the top of the inheritance tree.
		 */
		while (!idsOfUnprocessedSupertypes.isEmpty()) {

			/*
			 * We need to iterate through a separate collection because we
			 * remove elements from idsOfUnprocessedSupertypes, which would
			 * otherwise cause an issue with the iterator.
			 */
			for (String idOfgenSuperclass : genSuperclassesById.keySet()) {

				/* We do not want to process the same superclass twice. */
				if (!idsOfUnprocessedSupertypes.contains(idOfgenSuperclass)) {
					continue;
				}

				GenericClassInfo superclass = genSuperclassesById
						.get(idOfgenSuperclass);

				// get ids of the supertypes of this superclass
				SortedSet<String> supertypesOfSuperclass = superclass
						.supertypes();

				if (supertypesOfSuperclass == null
						|| supertypesOfSuperclass.size() == 0) {
					
					// copy relevant contents down to subtypes
					if (addAttributesAtBottom) {
						copyContentToSubtypes(genModel, superclass,
								PropertyCopyPositionIndicator.PROPERTY_COPY_BOTTOM);
					} else {
						copyContentToSubtypes(genModel, superclass,
								PropertyCopyPositionIndicator.PROPERTY_COPY_TOP);
					}

					idsOfUnprocessedSupertypes.remove(idOfgenSuperclass);

				} else {

					// determine if the supertypes of the current superclass are
					// not contained in the application schema or if they have
					// already been processed
					boolean noRelevantSupertypes = true;
					for (String supertypeId : supertypesOfSuperclass) {
						if (genModel
								.isInAppSchema(genModel.classById(supertypeId))
								&& idsOfUnprocessedSupertypes
										.contains(supertypeId)) {
							noRelevantSupertypes = false;
							break;
						}
					}
					if (noRelevantSupertypes) {

						/*
						 * copy relevant contents down to subtypes
						 *
						 * NOTE: does not copy associations - they are
						 * specifically handled later on
						 */
						if (addAttributesAtBottom) {
							copyContentToSubtypes(genModel, superclass,
									PropertyCopyPositionIndicator.PROPERTY_COPY_BOTTOM);
						} else {
							copyContentToSubtypes(genModel, superclass,
									PropertyCopyPositionIndicator.PROPERTY_COPY_TOP);
						}

						idsOfUnprocessedSupertypes.remove(idOfgenSuperclass);
					}
				}
			}
		}

		// create union classes for the classes that have subclasses (including
		// mixins, for the rare case that a property is of a mixin type)

		/*
		 * mapping of superclass to list of its subclasses key: superclass;
		 * value: list of all its subclasses (sorted by subclass name)
		 */
		Map<GenericClassInfo, List<GenericClassInfo>> allSubclassesByTheirSuperclass = new HashMap<GenericClassInfo, List<GenericClassInfo>>();

		for (GenericClassInfo genSuperclass : genSuperclassesById.values()) {

			GenericClassInfo genSuperclassUnion = new GenericClassInfo(genModel,
					genSuperclass.id() + "_union",
					genSuperclass.name() + "Union", Options.UNION);

			if (hasCode(genSuperclass)) {
				setCode(genSuperclassUnion, getCode(genSuperclass) + "_U");
			} else {
				setCode(genSuperclassUnion, genSuperclass.name() + "Union");
			}

			// TBD it would be good to use java enums for stereotypes
			genSuperclassUnion.setStereotype("union");
			genSuperclassUnion.setPkg(genSuperclass.pkg());
			/*
			 * NOTE for cast: the cast should be safe, because the package of
			 * genSuperclassUnion is that of genSuperclass, which is a
			 * GenericClassInfo (that always belongs to a GenericPackageInfo
			 */
			((GenericPackageInfo) genSuperclassUnion.pkg())
					.addClass(genSuperclassUnion);

			// add property for each non-abstract subclass
			HashSet<GenericClassInfo> subclasses = getAllSubclasses(
					genSuperclass,
					"The union created for all subtypes within the inheritance tree will thus be incomplete.");
			int seqNumIndex = 1;

			// sort the subclasses by name so that the resulting order of
			// choices is always the same
			List<GenericClassInfo> subclassesList = new ArrayList<GenericClassInfo>(
					subclasses);
			Collections.sort(subclassesList,
					new Comparator<GenericClassInfo>() {
						public int compare(GenericClassInfo f1,
								GenericClassInfo f2) {
							return f1.name().compareTo(f2.name());
						}
					});

			/*
			 * keep track of subclass list for later use (when associations are
			 * moved down
			 */
			allSubclassesByTheirSuperclass.put(genSuperclass, subclassesList);

			/*
			 * if the superclass is neither abstract nor a mixin, add it to the
			 * union
			 */
			if (!genSuperclass.isAbstract()
					&& !(genSuperclass.category() == Options.MIXIN)) {

				GenericPropertyInfo genSuperClassUnionProp = createPropertyForSuperClassUnion(
						genModel, genSuperclassUnion, genSuperclass,
						seqNumIndex);

				genSuperclassUnion.addProperty(genSuperClassUnionProp,
						PropertyCopyDuplicatBehaviorIndicator.ADD);
				seqNumIndex++;
			}

			/*
			 * if the subtype is neither abstract nor a mixin, add it to the
			 * union
			 */
			for (GenericClassInfo genCiSub : subclassesList) {

				if (genCiSub.isAbstract()
						|| genCiSub.category() == Options.MIXIN)
					continue;

				GenericPropertyInfo genSuperClassUnionProp = createPropertyForSuperClassUnion(
						genModel, genSuperclassUnion, genCiSub, seqNumIndex);

				genSuperclassUnion.addProperty(genSuperClassUnionProp,
						PropertyCopyDuplicatBehaviorIndicator.ADD);
				seqNumIndex++;

			}

			genSuperclassUnionsByName.put(genSuperclass.name(),
					genSuperclassUnion);
			genModel.addClass(genSuperclassUnion);
		}

		/*
		 * change the type of all attributes in the model that use one of the
		 * superclasses to the corresponding union, with the exception of the
		 * property being contained in the union itself (for the case that the
		 * superclass is neither abstract nor a mixin)
		 */
		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			// ignore association roles - they will be handled later on
			if (!genPi.isAttribute())
				continue;

			Type type = genPi.typeInfo();

			if (genSuperclassesById.containsKey(type.id)) {

				GenericClassInfo superclassUnion = genSuperclassUnionsByName
						.get(type.name);

				/*
				 * don't switch the type if the property is part of the union
				 * itself
				 */
				if (!genPi.inClass().id().equals(superclassUnion.id())) {
					genPi.setCategoryOfValue(superclassUnion.category());
					type.name = superclassUnion.name();
					type.id = superclassUnion.id();
				}
			}
		}

		// ------------------------------------- //
		// --- START of association handling --- //

		/*
		 * create copies of associations that belong to supertype classes (i.e.
		 * classes that have subclasses), one for each subclass
		 */

		Set<GenericAssociationInfo> genAisToAdd = new HashSet<GenericAssociationInfo>();

		for (GenericAssociationInfo genAi : genModel
				.selectedSchemaAssociations()) {

			PropertyInfo pi1 = genAi.end1();
			PropertyInfo pi2 = genAi.end2();

			String namePi1InClass = pi1.inClass().name();
			String namePi2InClass = pi2.inClass().name();

			/*
			 * check that at least one end of the association belongs to a
			 * superclass
			 */
			if (!(allSubclassesByTheirSuperclass.containsKey(pi1.inClass())
					|| allSubclassesByTheirSuperclass
							.containsKey(pi2.inClass()))) {

				continue;
			}

			/*
			 * check that both association ends and their inClasses are part of
			 * the selected schema
			 */
			if (!(pi1 instanceof GenericPropertyInfo
					&& pi1.inClass() instanceof GenericClassInfo
					&& pi2 instanceof GenericPropertyInfo
					&& pi2.inClass() instanceof GenericClassInfo)) {

				/*
				 * log warning, ensuring that class names to describe the
				 * association are in lexicographical order so that the warning
				 * is not logged twice (log message has prefix '??' to prevent
				 * duplicate messages) which is relevant in case there are
				 * multiple associations between the two classes
				 */
				if (!(pi1 instanceof GenericPropertyInfo
						&& pi1.inClass() instanceof GenericClassInfo)) {

					result.addWarning(null, 20336,
							(namePi1InClass.compareTo(namePi2InClass) <= 0)
									? namePi1InClass : namePi2InClass,
							(namePi1InClass.compareTo(namePi2InClass) <= 0)
									? namePi2InClass : namePi1InClass,
							namePi1InClass);

				} else {

					// pi2 is not an instance of GenericPropertyInfo
					result.addWarning(null, 20336,
							(namePi1InClass.compareTo(namePi2InClass) <= 0)
									? namePi1InClass : namePi2InClass,
							(namePi1InClass.compareTo(namePi2InClass) <= 0)
									? namePi2InClass : namePi1InClass,
							namePi2InClass);
				}

				continue;
			}

			// ================================================================
			// fine, we need to create copies of the association

			String separator = separatorForPropertyFromUnion;

			/*
			 * Note on cast: safe because the ends of a GenericAssociationInfo
			 * should be of type GenericPropertyInfo
			 */
			GenericPropertyInfo genPi1Orig = (GenericPropertyInfo) pi1;
			GenericPropertyInfo genPi2Orig = (GenericPropertyInfo) pi2;

			/*
			 * Note on cast: should be safe because we checked before that the
			 * inClasses of pi1 and pi2 are instances of GenericClassInfo
			 */
			GenericClassInfo genPi1InClass = (GenericClassInfo) pi1.inClass();
			GenericClassInfo genPi2InClass = (GenericClassInfo) pi2.inClass();

			/*
			 * compute new names and aliases for genPi1Orig and genPi2Orig
			 * (WARNING: NOT for the subtype specific property copies) which
			 * will be set later on, depending on the actual case
			 */
			String newGenPi1OrigName = pi1.name() + separator
					+ pi2.inClass().name();
			String newGenPi2OrigName = pi2.name() + separator
					+ pi1.inClass().name();

			String codePi1 = hasCode(pi1) ? getCode(pi1) : pi1.name();
			String codePi2 = hasCode(pi2) ? getCode(pi2) : pi2.name();
			String codePi1InClass = hasCode(genPi1InClass)
					? getCode(genPi1InClass) : genPi1InClass.name();
			String codePi2InClass = hasCode(genPi2InClass)
					? getCode(genPi2InClass) : genPi2InClass.name();

			String newAliasPi1Orig = (hasCode(pi1) || hasCode(genPi2InClass))
					? codePi1 + separator + codePi2InClass : null;
			String newAliasPi2Orig = (hasCode(pi2) || hasCode(genPi1InClass))
					? codePi2 + separator + codePi1InClass : null;

			/*
			 * now create the new associations; first handle case of subtypes on
			 * both ends, then for subtypes on one end only
			 */
			if (allSubclassesByTheirSuperclass.containsKey(pi1.inClass())
					&& allSubclassesByTheirSuperclass
							.containsKey(pi2.inClass())) {

				// subclasses exist for both ends

				// NOTE: the lists have been sorted by name before
				List<GenericClassInfo> subclassesPi1InClass = allSubclassesByTheirSuperclass
						.get(pi1.inClass());
				List<GenericClassInfo> subclassesPi2InClass = allSubclassesByTheirSuperclass
						.get(pi2.inClass());

				// check that the maps are not empty
				boolean checkFailed = false;
				if (subclassesPi1InClass == null
						|| subclassesPi1InClass.isEmpty()) {
					checkFailed = true;
					result.addError(null, 20337, pi1.inClass().name());
				}
				if (subclassesPi2InClass == null
						|| subclassesPi2InClass.isEmpty()) {
					checkFailed = true;
					result.addError(null, 20337, pi2.inClass().name());
				}
				if (checkFailed) {
					continue;
				}

				Multiplicity mPi1 = new Multiplicity(
						pi1.cardinality().toString());
				mPi1.minOccurs = 0;

				Multiplicity mPi2 = new Multiplicity(
						pi2.cardinality().toString());
				mPi2.minOccurs = 0;

				// create association copies for subclasses

				/*
				 * both lists with subclasses have been sorted - when we iterate
				 * through these lists, it is fine to have an overall index for
				 * the sequence number suffix; each association copy that is
				 * created will then be placed in a well-defined order that is
				 * defined by the order of elements in the lists
				 */
				int sequenceNumberIndex = 1;
				for (GenericClassInfo subclassPi1InClass : subclassesPi1InClass) {

					for (GenericClassInfo subclassPi2InClass : subclassesPi2InClass) {

						// compute new name and code/alias
						String newNamePi1 = pi1.name() + separator
								+ subclassPi2InClass.name();
						String newNamePi2 = pi2.name() + separator
								+ subclassPi1InClass.name();

						String codesubclassPi1InClass = hasCode(
								subclassPi1InClass)
										? getCode(subclassPi1InClass)
										: subclassPi1InClass.name();
						String codesubclassPi2InClass = hasCode(
								subclassPi2InClass)
										? getCode(subclassPi2InClass)
										: subclassPi2InClass.name();

						String newAliasPi1 = (hasCode(pi1)
								|| hasCode(subclassPi2InClass))
										? codePi1 + separator
												+ codesubclassPi2InClass
										: null;
						String newAliasPi2 = (hasCode(pi2)
								|| hasCode(subclassPi1InClass))
										? codePi2 + separator
												+ codesubclassPi1InClass
										: null;

						StructuredNumber newSnPi1 = pi1.sequenceNumber()
								.createCopyWithSuffix(sequenceNumberIndex);
						StructuredNumber newSnPi2 = pi2.sequenceNumber()
								.createCopyWithSuffix(sequenceNumberIndex);
						sequenceNumberIndex++;

						// create association copy
						GenericAssociationInfo aiCopy = createCopyAndSetEnds(
								genModel, genAi, newNamePi1, newAliasPi1,
								subclassPi1InClass, mPi1, newSnPi1, newNamePi2,
								newAliasPi2, subclassPi2InClass, mPi2, newSnPi2,
								false);

						genAisToAdd.add(aiCopy);
					}
				}

				/*
				 * if a superclass is neither abstract nor a mixin, establish
				 * associations to subclasses of the other superclass
				 */

				if (!(pi1.inClass().isAbstract()
						|| pi1.inClass().category() == Options.MIXIN)) {

					/*
					 * to be one the safe side, append a suffix to the sequence
					 * numbers of the ends in each association copy that is
					 * created
					 */
					sequenceNumberIndex = 1;
					for (GenericClassInfo subclassPi2InClass : subclassesPi2InClass) {

						// compute new name and code/alias
						String newNamePi1 = pi1.name() + separator
								+ subclassPi2InClass.name();
						String newNamePi2 = pi2.name() + separator
								+ genPi1InClass.name();

						String codesubclassPi2InClass = hasCode(
								subclassPi2InClass)
										? getCode(subclassPi2InClass)
										: subclassPi2InClass.name();

						String newAliasPi1 = (hasCode(pi1)
								|| hasCode(subclassPi2InClass))
										? codePi1 + separator
												+ codesubclassPi2InClass
										: null;
						String newAliasPi2 = (hasCode(pi2)
								|| hasCode(genPi1InClass))
										? codePi2 + separator + codePi1InClass
										: null;

						StructuredNumber newSnPi1 = pi1.sequenceNumber()
								.createCopyWithSuffix(sequenceNumberIndex);
						/*
						 * the value type of pi2 is always pi1.inClass() - by
						 * appending the suffix '0' we ensure that the property
						 * is listed first in all subclasses of pi2.inClass (and
						 * also that the property does not overwrite one that
						 * points to a subclass of pi1.inClass [established
						 * before] because it has the same sequence number)
						 */
						StructuredNumber newSnPi2 = pi2.sequenceNumber()
								.createCopyWithSuffix(0);
						sequenceNumberIndex++;

						// create association copy
						GenericAssociationInfo aiCopy = createCopyAndSetEnds(
								genModel, genAi, newNamePi1, newAliasPi1,
								genPi1InClass, mPi1, newSnPi1, newNamePi2,
								newAliasPi2, subclassPi2InClass, mPi2, newSnPi2,
								false);

						genAisToAdd.add(aiCopy);
					}
				}

				if (!(pi2.inClass().isAbstract()
						|| pi2.inClass().category() == Options.MIXIN)) {

					/*
					 * to be one the safe side, append a suffix to the sequence
					 * numbers of the ends in each association copy that is
					 * created
					 */
					sequenceNumberIndex = 1;
					for (GenericClassInfo subclassPi1InClass : subclassesPi1InClass) {

						// compute new name and code/alias
						String newNamePi1 = pi1.name() + separator
								+ genPi2InClass.name();
						String newNamePi2 = pi2.name() + separator
								+ subclassPi1InClass.name();

						String codesubclassPi1InClass = hasCode(
								subclassPi1InClass)
										? getCode(subclassPi1InClass)
										: subclassPi1InClass.name();

						String newAliasPi1 = (hasCode(pi1)
								|| hasCode(genPi2InClass))
										? codePi1 + separator + codePi2InClass
										: null;
						String newAliasPi2 = (hasCode(pi2)
								|| hasCode(subclassPi1InClass))
										? codePi2 + separator
												+ codesubclassPi1InClass
										: null;

						/*
						 * the value type of pi1 is always pi2.inClass() - by
						 * appending the suffix '0' we ensure that the property
						 * is listed first in all subclasses of pi1.inClass (and
						 * also that the property does not overwrite one that
						 * points to a subclass of pi2.inClass [established
						 * before] because it has the same sequence number)
						 */
						StructuredNumber newSnPi1 = pi1.sequenceNumber()
								.createCopyWithSuffix(0);
						StructuredNumber newSnPi2 = pi2.sequenceNumber()
								.createCopyWithSuffix(sequenceNumberIndex);
						sequenceNumberIndex++;

						// create association copy
						GenericAssociationInfo aiCopy = createCopyAndSetEnds(
								genModel, genAi, newNamePi1, newAliasPi1,
								subclassPi1InClass, mPi1, newSnPi1, newNamePi2,
								newAliasPi2, genPi2InClass, mPi2, newSnPi2,
								false);

						genAisToAdd.add(aiCopy);
					}
				}

				// also update roles of original association
				genPi1Orig.setName(newGenPi1OrigName);
				genPi2Orig.setName(newGenPi2OrigName);

				setCode(genPi1Orig, newAliasPi1Orig);
				setCode(genPi2Orig, newAliasPi2Orig);

				genPi1Orig.setCardinality(mPi1);
				genPi2Orig.setCardinality(mPi2);

			} else {

				// only one end has been split

				if (allSubclassesByTheirSuperclass.containsKey(pi1.inClass())) {

					List<GenericClassInfo> subclassesPi1InClass = allSubclassesByTheirSuperclass
							.get(pi1.inClass());

					if (subclassesPi1InClass == null
							|| subclassesPi1InClass.isEmpty()) {

						result.addError(null, 20337, pi1.inClass().name());

					} else {

						/*
						 * to be one the safe side, append a suffix to the
						 * sequence numbers of the ends in each association copy
						 * that is created
						 */
						int sequenceNumberIndex = 1;
						for (GenericClassInfo subclassPi1InClass : subclassesPi1InClass) {

							Multiplicity mPi2 = new Multiplicity(
									pi2.cardinality().toString());
							mPi2.minOccurs = 0;

							// compute new name and code/alias
							String newNamePi2 = pi2.name() + separator
									+ subclassPi1InClass.name();

							String codesubclassPi1InClass = hasCode(
									subclassPi1InClass)
											? getCode(subclassPi1InClass)
											: subclassPi1InClass.name();

							String newAliasPi2 = (hasCode(pi2)
									|| hasCode(subclassPi1InClass))
											? codePi2 + separator
													+ codesubclassPi1InClass
											: null;

							/*
							 * the value type of pi1 is always pi2.inClass() -
							 * by appending the suffix '0' we ensure that the
							 * property is listed first in all subclasses of
							 * pi1.inClass (and also that the property does not
							 * overwrite one that points to a subclass of
							 * pi2.inClass [established before] because it has
							 * the same sequence number)
							 */
							StructuredNumber newSnPi1 = pi1.sequenceNumber()
									.createCopyWithSuffix(0);
							StructuredNumber newSnPi2 = pi2.sequenceNumber()
									.createCopyWithSuffix(sequenceNumberIndex);
							sequenceNumberIndex++;

							// create association copy
							GenericAssociationInfo aiCopy = createCopyAndSetEnds(
									genModel, genAi, null, null,
									subclassPi1InClass, null, newSnPi1,
									newNamePi2, newAliasPi2, genPi2InClass,
									mPi2, newSnPi2, false);

							genAisToAdd.add(aiCopy);
						}

						// also update role of original association
						genPi2Orig.setName(newGenPi2OrigName);

						setCode(genPi2Orig, newAliasPi2Orig);

						Multiplicity mPi2 = new Multiplicity(
								pi2.cardinality().toString());
						mPi2.minOccurs = 0;

						genPi2Orig.setCardinality(mPi2);
					}

				} else {

					List<GenericClassInfo> subclassesPi2InClass = allSubclassesByTheirSuperclass
							.get(pi2.inClass());

					if (subclassesPi2InClass == null
							|| subclassesPi2InClass.isEmpty()) {

						result.addError(null, 20337, pi2.inClass().name());

					} else {

						/*
						 * to be one the safe side, append a suffix to the
						 * sequence numbers of the ends in each association copy
						 * that is created
						 */
						int sequenceNumberIndex = 1;
						for (GenericClassInfo subclassPi2InClass : subclassesPi2InClass) {

							Multiplicity mPi1 = new Multiplicity(
									pi1.cardinality().toString());
							mPi1.minOccurs = 0;

							// compute new name and code/alias
							String newNamePi1 = pi1.name() + separator
									+ subclassPi2InClass.name();

							String codesubclassPi2InClass = hasCode(
									subclassPi2InClass)
											? getCode(subclassPi2InClass)
											: subclassPi2InClass.name();

							String newAliasPi1 = (hasCode(pi1)
									|| hasCode(subclassPi2InClass))
											? codePi1 + separator
													+ codesubclassPi2InClass
											: null;

							StructuredNumber newSnPi1 = pi1.sequenceNumber()
									.createCopyWithSuffix(sequenceNumberIndex);
							/*
							 * the value type of pi2 is always pi1.inClass() -
							 * by appending the suffix '0' we ensure that the
							 * property is listed first in all subclasses of
							 * pi2.inClass (and also that the property does not
							 * overwrite one that points to a subclass of
							 * pi1.inClass [established before] because it has
							 * the same sequence number)
							 */
							StructuredNumber newSnPi2 = pi2.sequenceNumber()
									.createCopyWithSuffix(0);
							sequenceNumberIndex++;

							// create association copy
							GenericAssociationInfo aiCopy = createCopyAndSetEnds(
									genModel, genAi, newNamePi1, newAliasPi1,
									genPi1InClass, mPi1, newSnPi1, null, null,
									subclassPi2InClass, null, newSnPi2, false);

							genAisToAdd.add(aiCopy);
						}

						// also update roles of original association
						genPi1Orig.setName(newGenPi1OrigName);

						setCode(genPi1Orig, newAliasPi1Orig);

						Multiplicity mPi1 = new Multiplicity(
								pi1.cardinality().toString());
						mPi1.minOccurs = 0;

						genPi1Orig.setCardinality(mPi1);
					}
				}
			}
		}

		// add association copies to the model
		for (GenericAssociationInfo aiCopy : genAisToAdd) {
			genModel.addAssociation(aiCopy);
		}
		// --- END of association handling --- //
		// ----------------------------------- //

		/*
		 * now remove inheritance relationships for processed supertypes (and
		 * their subtypes), also remove processed supertypes if they are
		 * abstract or mixins
		 */

		/*
		 * remove inheritance relationships from leaf classes (set baseClass and
		 * supertypes to null)
		 */
		for (GenericClassInfo leafCi : genLeafclassesById.values()) {
			leafCi.setBaseClass(null);
			leafCi.setSupertypes(new TreeSet<String>()); // TODO use null
		}

		/*
		 * remove processed superclasses from the model if they are abstract or
		 * mixins, and remove inheritance relationships for non-abstract
		 * superclasses (set baseClass, supertypes and subtypes to null)
		 */
		for (GenericClassInfo superclass : genSuperclassesById.values()) {
			if (superclass.isAbstract()
					|| superclass.category() == Options.MIXIN) {
				genModel.remove(superclass);
			} else {
				superclass.setBaseClass(null);
				superclass.setSupertypes(new TreeSet<String>()); // TODO use
																	// null
				superclass.setSubtypes(new TreeSet<String>()); // TODO use null
			}
		}
	}

	/**
	 * @param genCls
	 *            Class to investigate - both the class itself and its subtypes
	 *            must not be <code>null</code>
	 * @return <code>true</code> if all of the subtypes are outside of the
	 *         selected schemas, else <code>false</code>
	 */
	private boolean allSubtypesOutsideSelectedSchemas(GenericClassInfo genCls) {

		Model model = genCls.model();

		for (String subtypeId : genCls.subtypes()) {
			ClassInfo subtype = model.classById(subtypeId);
			if (model.isInSelectedSchemas(subtype)) {
				return false;
			}
		}

		return true;
	}

	private GenericPropertyInfo createPropertyForSuperClassUnion(
			GenericModel genModel, GenericClassInfo genSuperclassUnion,
			GenericClassInfo genSuperclass, int seqNumIndex) {

		GenericPropertyInfo genSuperClassUnionProp = new GenericPropertyInfo(
				genModel, genSuperclassUnion.id() + "_choice" + seqNumIndex,
				toLowerCase(genSuperclass.name()), genSuperclass.category());

		genSuperClassUnionProp.setStereotype("");

		Type propType = new Type();
		propType.id = genSuperclass.id();
		propType.name = genSuperclass.name();
		genSuperClassUnionProp.setTypeInfo(propType);

		// TODO: configure non-standard tagged values via configuration
		// parameter
		TaggedValues taggedValues = options.taggedValueFactory();
		taggedValues.add("gmlImplementedByNilReason", "false");
		taggedValues.add("inlineOrByReference", "inlineOrByReference");
		taggedValues.add("isMetadata", "false");
		taggedValues.add(PARAM_MAXOCCURS, "");
		taggedValues.add("modified", "");
		taggedValues.add("name", "");
		taggedValues.add("physicalQuantity", "");
		taggedValues.add("profiles", "");
		taggedValues.add("recommendedMeasure", "");
		taggedValues.add("securityClassification", "");
		taggedValues.add("sequenceNumber", "" + seqNumIndex);
		taggedValues.add("xsdEncodingRule", "");
		genSuperClassUnionProp.setTaggedValues(taggedValues, false);

		genSuperClassUnionProp.setSequenceNumber(
				new StructuredNumber("" + seqNumIndex), false);

		genSuperClassUnionProp.setInClass(genSuperclassUnion);

		/*
		 * Careful: we created a new set of tagged values for the new property.
		 * Thus all modifications to the new property that might impact the
		 * tagged values, such as setting the code value, need to be performed
		 * after the new set of tagged values has been set
		 */
		if (hasCode(genSuperclass)) {
			setCode(genSuperClassUnionProp,
					toLowerCase(getCode(genSuperclass)));
		} else {
			setCode(genSuperClassUnionProp, toLowerCase(genSuperclass.name()));
		}

		return genSuperClassUnionProp;
	}

	/**
	 * If the name of the given class matches the regular expression of the
	 * pattern, or if one in the possible tree of supertypes does (they must all
	 * be contained in the application schema(s) selected for processing) then
	 * this method returns true, else false.
	 *
	 * @param genCi
	 * @param regex
	 * @return
	 */
	private boolean matchesRegexInSupertypeHierarchy(GenericClassInfo genCi,
			Pattern regex) {

		Matcher m = regex.matcher(genCi.name());

		if (m.matches()) {
			result.addDebug(null, 20344, genCi.name(), regex.pattern(), PARAM_INHERITANCE_INCLUDE_REGEX);
			return true;

		} else {
			result.addDebug(null, 20345, genCi.name(), regex.pattern(), PARAM_INHERITANCE_INCLUDE_REGEX);

			GenericModel model = genCi.model();

			SortedSet<String> supertypeIds = genCi.supertypes();

			if (supertypeIds != null && supertypeIds.size() > 0) {

				boolean matchFound = false;

				for (String supertypeId : supertypeIds) {

					ClassInfo supertype = model.classById(supertypeId);

					if (model.isInAppSchema(supertype)) {

						if (supertype instanceof GenericClassInfo) {

							GenericClassInfo genSupertype = (GenericClassInfo) supertype;

							boolean tmp = matchesRegexInSupertypeHierarchy(
									genSupertype, regex);

							if (tmp) {
								matchFound = true;
								break;
							}

						} else {

							/*
							 * the most likely reason that the supertype is not
							 * a GenericClassInfo is that it is not contained in
							 * the schema selected for processing - and thus
							 * does not produce a match
							 */
						}

					} else {
						/*
						 * the supertype is not contained in the schema selected
						 * for processing
						 */
					}
				}

				return matchFound;

			} else {

				return false;
			}
		}
	}

	/**
	 * Determines if a code value exists (is not null and does not only contain
	 * whitespace) for the given Info object, regardless where the code value is
	 * stored (alias or a tagged value).
	 *
	 * @param info
	 * @return true if the Info object has a non-empty code value, else false
	 */
	private boolean hasCode(Info info) {

		String codevalue = null;

		if (tvNameForCodeValue == null) {
			codevalue = info.aliasName();
		} else {
			codevalue = info.taggedValue(tvNameForCodeValue);
		}

		if (codevalue != null && codevalue.trim().length() > 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Turns the first character of the given string into lower case.
	 *
	 * @param s
	 *            String to convert
	 * @return New string object with content of given string, just the first
	 *         character set to lower case.
	 */
	private String toLowerCase(String s) {
		if (s == null)
			return null;
		else {

			String c = "" + Character.toLowerCase(s.charAt(0));

			if (s.length() == 1)
				return c;
			else
				return c + s.substring(1, s.length());
		}
	}

	/**
	 * Creates a copy of an association to connect two given classes,
	 * registering the copied components in the model and the classes that the
	 * new association connects.
	 *
	 * <ul>
	 * <li>Creates a copy of the association and both of its ends.</li>
	 * <li>Updates type info and inClass in both ends to use the given
	 * GenericClassInfos.</li>
	 * <li>Updates reverse property info to use the copies of the role ends that
	 * belong to the new association.</li>
	 * <li>Role names are updated if provided.</li>
	 * <li>registers the new association (if desired - controlled via parameter)
	 * and the new properties (for the association ends) in the model and the
	 * classes that the association connects</li>
	 * </ul>
	 * <p>
	 * NOTE: does not support copying of a possibly existing association class;
	 * such a class would be ignored, that is the resulting association won't
	 * have it
	 *
	 * @param genAi
	 *            association that shall be copied
	 * @param newEnd1Rolename
	 *            can be <code>null</code> (then the existing name is used
	 *            as-is)
	 * @param newEnd1InClass
	 *            inClass for end1
	 * @param newCardinalityEnd1
	 *            can be <code>null</code> (then the existing cardinality is
	 *            used as-is)
	 * @param newSequenceNumberEnd1
	 *            sequenceNumber that shall be used for end1
	 * @param newEnd2Rolename
	 *            can be <code>null</code> (then the existing name is used
	 *            as-is)
	 * @param newEnd2InClass
	 *            inClass for end2
	 * @param newCardinalityEnd2
	 *            can be <code>null</code> (then the existing cardinality is
	 *            used as-is)
	 * @param newSequenceNumberEnd2
	 *            sequenceNumber that shall be used for end2
	 * @return
	 */
	private GenericAssociationInfo createCopyAndSetEnds(GenericModel genModel,
			GenericAssociationInfo genAi, String newEnd1Rolename,
			String newEnd1Alias, GenericClassInfo newEnd1InClass,
			Multiplicity newCardinalityEnd1,
			StructuredNumber newSequenceNumberEnd1, String newEnd2Rolename,
			String newEnd2Alias, GenericClassInfo newEnd2InClass,
			Multiplicity newCardinalityEnd2,
			StructuredNumber newSequenceNumberEnd2,
			boolean registerAssociationCopyInModel) {

		String name1 = newEnd1InClass.name();
		String name2 = newEnd2InClass.name();

		String nameRoleEnd1 = (newEnd1Rolename == null) ? genAi.end1().name()
				: newEnd1Rolename;
		String nameRoleEnd2 = (newEnd2Rolename == null) ? genAi.end2().name()
				: newEnd2Rolename;

		/*
		 * Future work: create copy of potentially existing association class;
		 * for now, log warning if an association class exists (ensure that
		 * class names to describe the association are in lexicographical order
		 * so that the warning is not logged twice [log message has prefix '??'
		 * to prevent duplicate messages] which is relevant in case there are
		 * multiple such associations between the two classes)
		 */
		if (genAi.assocClass() != null) {

			result.addWarning(null, 20334,
					(name1.compareTo(name2) <= 0) ? name1 : name2,
					(name1.compareTo(name2) <= 0) ? name2 : name1);
		}

		GenericAssociationInfo aiCopy = genModel.createCopy(genAi,
				genAi.id() + "_copyBetweenClasses_" + name1 + "_and_" + name2
						+ "_for_roles_" + nameRoleEnd1 + "_and_"
						+ nameRoleEnd2);

		// create property copies
		GenericPropertyInfo genPi1 = genModel.createCopy(genAi.end1(),
				genAi.end1().id() + "_copyForAssociationBetweenClasses_" + name1
						+ "_and_" + name2);

		/*
		 * ensure that "sequenceNumber" tagged value is also updated
		 */
		genPi1.setSequenceNumber(newSequenceNumberEnd1, true);
		genPi1.setInClass(newEnd1InClass);
		genPi1.setAssociation(aiCopy);
		Type tiOfPi1 = genPi1.typeInfo();
		tiOfPi1.id = newEnd2InClass.id();
		tiOfPi1.name = newEnd2InClass.name();
		if (newEnd1Rolename != null) {
			genPi1.setName(newEnd1Rolename);
		}
		if (newEnd1Alias != null) {
			setCode(genPi1, newEnd1Alias);
		}
		if (newCardinalityEnd1 != null) {
			genPi1.setCardinality(newCardinalityEnd1);
		}

		GenericPropertyInfo genPi2 = genModel.createCopy(genAi.end2(),
				genAi.end2().id() + "_copyForAssociationBetweenClasses_" + name1
						+ "_and_" + name2);

		/*
		 * ensure that "sequenceNumber" tagged value is also updated
		 */
		genPi2.setSequenceNumber(newSequenceNumberEnd2, true);
		genPi2.setInClass(newEnd2InClass);
		genPi2.setAssociation(aiCopy);
		Type tiOfPi2 = genPi2.typeInfo();
		tiOfPi2.id = newEnd1InClass.id();
		tiOfPi2.name = newEnd1InClass.name();
		if (newEnd2Rolename != null) {
			genPi2.setName(newEnd2Rolename);
		}
		if (newEnd2Alias != null) {
			setCode(genPi2, newEnd2Alias);
		}
		if (newCardinalityEnd2 != null) {
			genPi2.setCardinality(newCardinalityEnd2);
		}

		genPi1.setReverseProperty(genPi2);
		genPi2.setReverseProperty(genPi1);

		aiCopy.setEnd1(genPi1);
		aiCopy.setEnd2(genPi2);

		// register new properties in model maps
		genModel.getGenProperties().put(genPi1.id(), genPi1);
		genModel.getGenProperties().put(genPi2.id(), genPi2);

		if (registerAssociationCopyInModel) {
			genModel.getGenAssociations().put(aiCopy.id(), aiCopy);
		}

		// register new association roles as properties of their inClasses
		if (genPi1.isNavigable()) {
			newEnd1InClass.addProperty(genPi1,
					PropertyCopyDuplicatBehaviorIndicator.ADD);
		}

		if (genPi2.isNavigable()) {
			newEnd2InClass.addProperty(genPi2,
					PropertyCopyDuplicatBehaviorIndicator.ADD);
		}

		return aiCopy;
	}

	/**
	 * @param genCi
	 * @param message
	 *            If a subtype that is not an instance of GenericClassInfo
	 *            (likely because the subtype is not part of the schema selected
	 *            for processing - is encountered, this message details the
	 *            potential issue this could cause. Can be <code>null</code>.
	 * @return The set of all direct or indirect subclasses of the given class,
	 *         or <code>null</code> if the class has no subclasses.
	 */
	private HashSet<GenericClassInfo> getAllSubclasses(GenericClassInfo genCi,
			String message) {

		String issueMessage = message == null ? "" : message;

		if (genCi.subtypes() == null || genCi.subtypes().isEmpty())
			return null;
		else {
			HashSet<GenericClassInfo> subtypes = new HashSet<GenericClassInfo>();

			for (String subtypeId : genCi.subtypes()) {

				ClassInfo subtype = genCi.model().classById(subtypeId);

				if (subtype instanceof GenericClassInfo) {

					GenericClassInfo genSubtype = (GenericClassInfo) subtype;

					subtypes.add(genSubtype);
					HashSet<GenericClassInfo> subsubtypes = getAllSubclasses(
							genSubtype, message);
					if (subsubtypes != null) {
						subtypes.addAll(subsubtypes);
					}

				} else {

					result.addWarning(null, 20320, subtype.name(), genCi.name(),
							issueMessage);
				}
			}

			return subtypes;
		}
	}

	/**
	 * Copies the properties of a given class to its subtypes. If a subtype
	 * already contains a property with the same name of one of the
	 * supertype-properties, the supertype-property is ignored (in other words
	 * the subtype-property overrides the supertype-property).
	 *
	 * @param genModel
	 *            model with all relevant classes
	 * @param genCi
	 *            the class from which all properties shall be copied into its
	 *            subtypes
	 */
	private void copyContentToSubtypes(GenericModel genModel,
			GenericClassInfo genCi, PropertyCopyPositionIndicator pcpi) {

		SortedSet<String> subtypeIds = genCi.subtypes();
		if (subtypeIds == null || subtypeIds.isEmpty())
			return;

		for (String subtypeId : subtypeIds) {

			ClassInfo subtype = genCi.model().classById(subtypeId);

			if (subtype instanceof GenericClassInfo) {

				GenericClassInfo genSubtype = (GenericClassInfo) subtype;

				genModel.copyClassContent(genCi, genSubtype, pcpi,
						PropertyCopyDuplicatBehaviorIndicator.IGNORE_UNRESTRICT);
			} else {

				result.addInfo(null, 20319, subtype.name(), genCi.name());
			}

		}

	}

	/**
	 * Same as
	 * {@link #copyContentToSubtypes(GenericModel, GenericClassInfo, PropertyCopyPositionIndicator)}
	 * , just with PropertyCopyPositionIndicator.PROPERTY_COPY_TOP as fixed
	 * value
	 */
	private void copyContentToSubtypes(GenericModel genModel,
			GenericClassInfo genCi) {
		copyContentToSubtypes(genModel, genCi,
				PropertyCopyPositionIndicator.PROPERTY_COPY_TOP);
	}

	private void applyRuleOptionality(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		ShapeChangeResult result = genModel.result();

		// first, get the list of type names from parameter enforceOptionality
		String[] typesToEnforceOptionality = trfConfig
				.getListParameterValue(TRANSFORMER_ENFORCE_OPTIONALITY);

		// if no type names are given via the enforceOptionality parameter,
		// return
		if (typesToEnforceOptionality == null) {
			result.addWarning(null, 20306);
			return;
		} else {

			Set<String> typeSet = new HashSet<String>(
					Arrays.asList(typesToEnforceOptionality));

			for (GenericPropertyInfo genPi : genModel
					.selectedSchemaProperties()) {

				if (typeSet.contains(genPi.typeInfo().name)) {
					genPi.cardinality().minOccurs = 0;
				}
			}
		}

	}

	/**
	 * The minimum multiplicity of each property whose type has a
	 * 'valueOrReason' property that is of a union-direct type is set to 0.
	 *
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRulePropUnionDirectOptionality(GenericModel genModel,
			TransformerConfiguration trfConfig) {

		// NOTE: this is a hotfix for creation of a specific GML 2.1 example
		// schema

		ShapeChangeResult result = genModel.result();

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			ClassInfo typeCi = genModel.classById(genPi.typeInfo().id);

			if (typeCi != null) {

				if (typeCi.name().endsWith("Meta")) {

					PropertyInfo piOfTypeCi = typeCi.property("valueOrReason");

					if (piOfTypeCi != null) {

						ClassInfo typeCi2 = genModel
								.classById(piOfTypeCi.typeInfo().id);

						// FIXME workaround because the call of
						// matches("rule-xsd-cls-union-direct") within
						// isUnionDirect() of typeCi2 ClassInfoImpl does not
						// work
						// properly within a transformer (because it depends on
						// encoding rules defined in targets)
						if (typeCi2 != null
								&& typeCi2.category() == Options.UNION
								&& typeCi2.name().endsWith("Reason")
								&& typeCi2.hasNilReason()
								&& typeCi2.properties().size() == 2) {

							genPi.cardinality().minOccurs = 0;

							// PropertyInfo valuePi = typeCi2.property("value");
							// if(valuePi != null) {
							// genPi.typeInfo().id = valuePi.typeInfo().id;
							// genPi.typeInfo().name = valuePi.typeInfo().name;
							// }
						}

					}
				} else if (typeCi.name().endsWith("Reason")
						&& !genPi.inClass().name().endsWith("Meta")) {

					// FIXME - see other workaround further above
					if (typeCi.category() == Options.UNION
							&& typeCi.hasNilReason()
							&& typeCi.properties().size() == 2) {

						genPi.cardinality().minOccurs = 0;

						PropertyInfo valuePi = typeCi.property("value");
						if (valuePi != null) {
							genPi.typeInfo().id = valuePi.typeInfo().id;
							genPi.typeInfo().name = valuePi.typeInfo().name;
						}
					}
				}
			}
		}

		for (GenericClassInfo genCi : genModel.selectedSchemaClasses()) {

			if (genCi.name().endsWith("Meta") && genCi.properties().size() == 1
					&& genCi.propertyByName("valueOrReason") != null) {

				/*
				 * NOTE for cast: the cast should be safe, because the
				 * valueOrReason property belongs to a GenericClassInfo (genCi)
				 */
				GenericPropertyInfo genPi = (GenericPropertyInfo) genCi
						.propertyByName("valueOrReason");

				ClassInfo typeCi = genModel.classById(genPi.typeInfo().id);

				PropertyInfo valuePi = typeCi.property("value");
				if (valuePi != null) {
					genPi.typeInfo().id = valuePi.typeInfo().id;
					genPi.typeInfo().name = valuePi.typeInfo().name;
				} else {
					result.addWarning(null, 20307, typeCi.name());
				}
			}
		}
	}

	/**
	 * Applies all necessary model modifications for the rule
	 * "rule-trf-prop-flatten-ONINAs".
	 *
	 * @param genModel
	 * @param result
	 * @param trfConfig
	 * @param options
	 */
	private void applyRuleONINAs(GenericModel model,
			TransformerConfiguration trfConfig) {

		Options options = model.options();

		SortedSet<GenericPackageInfo> appSchemas = model.selectedSchemas();
		if (appSchemas == null || appSchemas.size() == 0)
			return;

		for (PackageInfo appSchema : appSchemas) {

			boolean booleanReasonProcessed = false;
			GenericClassInfo booleanWithOninaCi = null;
			Type booleanWithOninaType = null;

			SortedSet<ClassInfo> appSchemaClasses = model.classes(appSchema);

			// handle the unlikely case that the application schema has no
			// classes
			if (appSchemaClasses == null || appSchemaClasses.size() == 0)
				continue;

			Map<String, Type> reasonTypeIdToValueType = new HashMap<String, Type>();
			Map<String, GenericClassInfo> reasonUnionsByName = new HashMap<String, GenericClassInfo>();
			Set<String> reasonTypeValueTypeNames = new HashSet<String>();
			Set<String> reasonPropertyValueTypeIds = new HashSet<String>();

			// identify all the union classes that have a name ending in
			// "Reason"; thereby also check if the model contains a
			// BooleanReason (creating the BooleanWithONINA enumeration for
			// later use)
			for (ClassInfo ci : appSchemaClasses) {

				if (ci.category() == Options.UNION
						&& ci.name().endsWith("Reason")) {

					/*
					 * NOTE for cast: the cast should be safe, because ci is
					 * part of a schema that has been selected for processing
					 * (the contents of which should have been parsed to generic
					 * types)
					 */
					reasonUnionsByName.put(ci.name(), (GenericClassInfo) ci);

					if (ci.name().equals("BooleanReason")
							&& booleanWithOninaCi == null) {

						// create new enumeration BooleanWithONINA
						booleanWithOninaCi = new GenericClassInfo(model,
								ci.id() + "_ONINARep", "BooleanWithONINA",
								Options.ENUMERATION);

						// set remaining properties required by Info interface
						booleanWithOninaCi.setAliasName("");
						setCode(booleanWithOninaCi, "");
						booleanWithOninaCi.setDefinition("");

						// TBD: is there an easy way to get all the relevant
						// tagged values for an enumeration?
						TaggedValues taggedValues = options
								.taggedValueFactory();
						taggedValues.add("modified", "");
						if (ci.taggedValue("xsdEncodingRule") != null) {
							taggedValues.add("xsdEncodingRule",
									ci.taggedValue("xsdEncodingRule"));
						}
						booleanWithOninaCi.setTaggedValues(taggedValues, false);

						// set properties required by ClassInfo interface
						booleanWithOninaCi.setXmlSchemaType(null);
						booleanWithOninaCi.setIncludePropertyType(
								ci.includePropertyType());
						booleanWithOninaCi.setIncludeByValuePropertyType(
								ci.includeByValuePropertyType());
						booleanWithOninaCi.setIsCollection(false);
						booleanWithOninaCi.setAsDictionary(false);
						booleanWithOninaCi.setAsGroup(false);
						booleanWithOninaCi.setAsCharacterString(false);
						booleanWithOninaCi.setHasNilReason(false);
						booleanWithOninaCi.setPkg(ci.pkg());
						/*
						 * NOTE for cast: the cast should be safe, because ci is
						 * part of a schema that has been selected for
						 * processing (the contents of which should have been
						 * parsed to generic types) - and thus is a
						 * GenericClassInfo, which always belongs to a
						 * GenericPackageInfo
						 */
						((GenericPackageInfo) ci.pkg())
								.addClass(booleanWithOninaCi);
						booleanWithOninaCi.setIsAbstract(false);
						booleanWithOninaCi.setIsLeaf(false);
						booleanWithOninaCi.setAssocInfo(null);
						booleanWithOninaCi.setSupertypes(new TreeSet<String>());
						booleanWithOninaCi.setSubtypes(null);
						booleanWithOninaCi.setBaseClass(null);

						booleanWithOninaCi.setProperties(ci.properties());
						TreeMap<StructuredNumber, PropertyInfo> properties = new TreeMap<StructuredNumber, PropertyInfo>();

						StructuredNumber s1 = new StructuredNumber("1");
						GenericPropertyInfo falseEnumProp = createEnumerationProperty(
								model, "false", "1000", booleanWithOninaCi, s1);
						setCode(falseEnumProp, "1000");
						falseEnumProp.setDefinition("False");
						properties.put(s1, falseEnumProp);

						model.register(falseEnumProp);

						StructuredNumber s2 = new StructuredNumber("2");
						GenericPropertyInfo trueEnumProp = createEnumerationProperty(
								model, "true", "1001", booleanWithOninaCi, s2);
						setCode(trueEnumProp, "1001");
						trueEnumProp.setDefinition("True");
						properties.put(s2, trueEnumProp);

						model.register(trueEnumProp);

						StructuredNumber s3 = new StructuredNumber("3");
						GenericPropertyInfo noInfoProp = createEnumerationProperty(
								model, "noInformation", "-999999",
								booleanWithOninaCi, s3);
						setCode(noInfoProp, "-999999");
						noInfoProp.setDefinition("No Information");
						properties.put(s3, noInfoProp);

						model.register(noInfoProp);

						// JE: 'notApplicable' and 'other' not needed right now,
						// maybe later on

						// StructuredNumber s4 = new StructuredNumber("4");
						// GenericPropertyInfo notApplicProp =
						// createEnumerationProperty(
						// model, "notApplicable", "998",
						// booleanWithOninaCi, s4);
						// notApplicProp
						// .setDocumentation(options.definitionSeparator()+"\r\nNot
						// Applicable");
						// properties.put(s4, notApplicProp);
						//
						// StructuredNumber s5 = new StructuredNumber("5");
						// GenericPropertyInfo otherProp =
						// createEnumerationProperty(
						// model, "other", "999", booleanWithOninaCi, s5);
						// otherProp.setDocumentation(options.definitionSeparator()+"\r\nOther");
						// properties.put(s5, otherProp);

						booleanWithOninaCi.setProperties(properties);

						booleanWithOninaCi
								.setConstraints(new Vector<Constraint>());
						booleanWithOninaCi.setSuppressed(false);
						booleanWithOninaCi.setAsDictionaryGml33(false);

						model.addClass(booleanWithOninaCi);

						booleanWithOninaType = new Type();
						booleanWithOninaType.id = booleanWithOninaCi.id();
						booleanWithOninaType.name = booleanWithOninaCi.name();

						model.register(booleanWithOninaCi);
					}
				}
			}

			// if there were no XxxReason union classes, continue
			if (reasonUnionsByName.isEmpty() && !booleanReasonProcessed)
				continue;

			// for each of the XxxReason classes, look up the type of their
			// value property
			for (GenericClassInfo reasonUnionCi : reasonUnionsByName.values()) {

				PropertyInfo valueP = reasonUnionCi.property("value");
				if (valueP == null) {

					result.addWarning(null, 20339, reasonUnionCi.name());
					continue;
				}

				// get the type info for value property: internal id within the
				// model and the local, unqualified name
				Type valuePType = valueP.typeInfo();

				if (valuePType.name.equalsIgnoreCase("Boolean")) {
					reasonTypeIdToValueType.put(reasonUnionCi.id(),
							booleanWithOninaType);
				} else {
					reasonTypeIdToValueType.put(reasonUnionCi.id(), valuePType);

					// keep track of the names of the types used in the
					// XxxReason union classes to get them later on for adding
					// the noInformation, not Applicable and other enums, except
					// for the special case of BooleanReason (which is already
					// covered)

					// TBD: is it safe to use the type name here - shouldn't we
					// use the id?
					reasonTypeValueTypeNames.add(valuePType.name);
				}
			}

			/*
			 * for each of the XxxReason classes, look up the type of their
			 * reason property, and store it so that it can be removed at the
			 * end of this rule.
			 */
			for (GenericClassInfo reasonUnionCi : reasonUnionsByName.values()) {

				PropertyInfo reasonP = reasonUnionCi.property("reason");
				if (reasonP == null) {

					/*
					 * This does not influence the end result - in which the
					 * XxxReason unions and the value type(s) of their 'reason'
					 * properties have been removed from the model.
					 */
				} else {

					/*
					 * get the type info for reason property: internal id within
					 * the model - so that it can be removed at the end of the
					 * rule.
					 */
					Type reasonPType = reasonP.typeInfo();

					reasonPropertyValueTypeIds.add(reasonPType.id);
				}
			}

			/*
			 * Add ONINA enum properties to all enumerations (except
			 * BooleanWithONINA enumeration which is already complete [if used
			 * at all]).
			 *
			 * Do not add ONINA enums if they already exist in an enumeration
			 * (ignore duplicates).
			 */
			for (String typeName : reasonTypeValueTypeNames) {

				ClassInfo ci = model.classByName(typeName);

				if (ci == null) {

					this.result.addWarning(null, 20303, typeName);

				} else if (ci.category() == Options.ENUMERATION) {

					if (ci instanceof GenericClassInfo) {

						GenericClassInfo genCi = (GenericClassInfo) ci;

						int maxSequenceNumber = Integer.MIN_VALUE;
						Set<StructuredNumber> enumSeqNumbers = genCi
								.properties().keySet();
						// look up highest sequence number in list of existing
						// properties (via first component of the structured
						// number)
						for (StructuredNumber strucNum : enumSeqNumbers) {
							if (strucNum.components[0] > maxSequenceNumber) {
								maxSequenceNumber = strucNum.components[0];
							}
						}

						maxSequenceNumber++;
						StructuredNumber snNoInformation = new StructuredNumber(
								maxSequenceNumber);
						GenericPropertyInfo noInfoProp = createEnumerationProperty(
								model, "noInformation", "-999999", genCi,
								snNoInformation);
						setCode(noInfoProp, "-999999");
						noInfoProp.setDefinition("No Information");
						model.add(noInfoProp, genCi,
								PropertyCopyDuplicatBehaviorIndicator.IGNORE);

						maxSequenceNumber++;
						StructuredNumber snNotApplicable = new StructuredNumber(
								maxSequenceNumber);
						GenericPropertyInfo notApplicProp = createEnumerationProperty(
								model, "notApplicable", "998", genCi,
								snNotApplicable);
						setCode(notApplicProp, "998");
						notApplicProp.setDefinition("Not Applicable");
						model.add(notApplicProp, genCi,
								PropertyCopyDuplicatBehaviorIndicator.IGNORE);

						maxSequenceNumber++;
						StructuredNumber snOther = new StructuredNumber(
								maxSequenceNumber);
						GenericPropertyInfo otherProp = createEnumerationProperty(
								model, "other", "999", genCi, snOther);
						setCode(otherProp, "999");
						otherProp.setDefinition("Other");
						model.add(otherProp, genCi,
								PropertyCopyDuplicatBehaviorIndicator.IGNORE);

					} else {

						result.addWarning(null, 20323, ci.name());
					}

				} else {
					/*
					 * fine - can be another simple type like CharacterString,
					 * Integer, Measure - for which ONINAs are encoded as
					 * special values
					 */
				}
			}

			// now update the type of the class properties within the
			// application schema

			String propTypeId;
			for (ClassInfo ci : appSchemaClasses) {

				Collection<PropertyInfo> ciProperties = ci.properties()
						.values();
				if (ciProperties == null || ciProperties.size() == 0)
					continue;

				for (PropertyInfo pi : ciProperties) {

					propTypeId = pi.typeInfo().id;

					if (reasonTypeIdToValueType.containsKey(propTypeId)) {

						/*
						 * NOTE for cast: the cast should be safe, because pi
						 * belongs to a ClassInfo (ci) that is part of an
						 * application schema which has been selected for
						 * processing (and thus should be a GenericClassInfo).
						 */
						GenericPropertyInfo genPi = (GenericPropertyInfo) pi;

						Type valueTypeToUse = reasonTypeIdToValueType
								.get(propTypeId);

						genPi.copyTypeInfo(valueTypeToUse);

						/*
						 * Ensure that we use the correct value category. We
						 * cannot simply use enumeration because in some cases
						 * (e.g. when using complex datatypes as value) an
						 * XxxReason <<union>> may not use an enumeration as
						 * type of its 'value' property.
						 */
						ClassInfo valueTypeToUseCi = model
								.classById(valueTypeToUse.id);

						if (valueTypeToUseCi != null) {
							genPi.setCategoryOfValue(
									valueTypeToUseCi.category());
						} else {
							result.addWarning(null, 20340, genPi.name(),
									genPi.inClass().name(),
									valueTypeToUse.name);
							genPi.setCategoryOfValue(Options.UNKNOWN);
						}
					}
				}
			}

			// now remove XxxReason union classes
			model.remove(reasonUnionsByName.values());

			/*
			 * also remove the classes that were used by the "reason" property
			 * inside XxxReason unions as value types
			 */
			for (String reasonPropertyValueTypeId : reasonPropertyValueTypeIds) {
				model.removeByClassId(reasonPropertyValueTypeId);
			}

		}
	}

	/**
	 * @see #RULE_TRF_NAV_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleRemoveObjectToFeatureTypeNavigability(
			GenericModel genModel, TransformerConfiguration trfConfig) {

		/* --- determine and validate parameter values --- */

		String regex = trfConfig
				.getParameterValue(PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX);

		if (regex != null) {

			regex = regex.trim();

			if (regex.length() == 0) {
				// the regular expression is required but was not
				// provided
				result.addError(null, 20001,
						PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX,
						RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY);
				return;
			}

		} else {

			// the suffix regular expression is required but was not provided
			result.addError(null, 20002, PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX,
					RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY);
			return;
		}

		boolean includeObjectTypes = false;
		String includeObjectTypes_ = trfConfig
				.getParameterValue(PARAM_INCLUDE_OBJECT_NAV);
		if (includeObjectTypes_ != null
				&& Boolean.parseBoolean(includeObjectTypes_.trim())) {
			includeObjectTypes = true;
		}

		Pattern pattern = null;

		try {

			pattern = Pattern.compile(regex);

		} catch (PatternSyntaxException e) {

			result.addError(null, 20003, PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX,
					RULE_TRF_PROP_REMOVE_OBJECT_TO_FEATURE_TYPE_NAVIGABILITY,
					regex, e.getMessage());
			return;
		}

		/* --- apply rule --- */
		Set<GenericPropertyInfo> genPisToRemove = new HashSet<GenericPropertyInfo>();

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			if (genPi.inClass().category() == Options.OBJECT && (genPi
					.categoryOfValue() == Options.FEATURE
					|| (includeObjectTypes
							&& genPi.categoryOfValue() == Options.OBJECT))) {

				Matcher matcher = pattern.matcher(genPi.inClass().name());

				if (matcher.matches()) {

					genPisToRemove.add(genPi);
					result.addDebug(null, 20344, genPi.inClass().name(), regex, PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX);
				} else {
					result.addDebug(null, 20345, genPi.inClass().name(), regex, PARAM_OBJECT_TO_FEATURE_TYPE_NAV_REGEX);
				}
			}
		}

		for (GenericPropertyInfo genPiToRemove : genPisToRemove) {

			genModel.remove(genPiToRemove, true);
		}
	}

	/**
	 * @see #RULE_TRF_PROP_REMOVE_NAVIGABILITY_BASEDON_ISFLATTARGET
	 * @param genModel
	 * @param trfConfig
	 */
	private void applyRuleRemoveNavigabilityBasedOnFlatTargetSetting(
			GenericModel genModel, TransformerConfiguration trfConfig) {

		Set<GenericPropertyInfo> genPisToRemove = new HashSet<GenericPropertyInfo>();

		for (GenericPropertyInfo genPi : genModel.selectedSchemaProperties()) {

			if (genPi.association() != null) {

				String isFlatTarget_genPi = genPi
						.taggedValue(TAGGED_VALUE_IS_FLAT_TARGET);

				if (isFlatTarget_genPi != null
						&& isFlatTarget_genPi.trim().equalsIgnoreCase("true")) {

					// remove navigability of genPi - so remove genPi completely
					genPisToRemove.add(genPi);
				}

				/*
				 * now also check if isFlatTarget setting would remove the
				 * association completely - log a warning if it did
				 */
				AssociationInfo ai = genPi.association();
				PropertyInfo pi1, pi2;

				/*
				 * ensure that pi1 and pi2 are processed in some lexicographical
				 * order so that the order in which they are reported in a
				 * potential warning message is always the same to avoid
				 * duplicate messages.
				 */
				if (ai.end1().id().compareTo(ai.end2().id()) <= 0) {
					pi1 = ai.end1();
					pi2 = ai.end2();
				} else {
					pi1 = ai.end2();
					pi2 = ai.end1();
				}

				String isFlatTarget_pi1_tv = pi1
						.taggedValue(TAGGED_VALUE_IS_FLAT_TARGET);
				String isFlatTarget_pi2_tv = pi2
						.taggedValue(TAGGED_VALUE_IS_FLAT_TARGET);

				boolean isFlatTarget_pi1 = isFlatTarget_pi1_tv != null
						? Boolean.parseBoolean(isFlatTarget_pi1_tv.trim())
						: false;
				boolean isFlatTarget_pi2 = isFlatTarget_pi2_tv != null
						? Boolean.parseBoolean(isFlatTarget_pi2_tv.trim())
						: false;

				if ((pi1.isNavigable() && pi2.isNavigable() && isFlatTarget_pi1
						&& isFlatTarget_pi2)
						|| (pi1.isNavigable() && !pi2.isNavigable()
								&& isFlatTarget_pi1)
						|| (pi2.isNavigable() && !pi1.isNavigable()
								&& isFlatTarget_pi2)) {

					/*
					 * whole association will be removed; log a warning
					 */
					result.addWarning(null, 20325, pi1.name(),
							pi1.inClass().name(), pi2.name(),
							pi2.inClass().name());
				}
			}
		}

		for (GenericPropertyInfo genPiToRemove : genPisToRemove) {

			genModel.remove(genPiToRemove, true);
		}
	}

	/**
	 * @param model
	 * @param enumName
	 * @param enumAlias
	 * @param ci
	 * @param strucNum
	 * @return
	 */
	private GenericPropertyInfo createEnumerationProperty(GenericModel model,
			String enumName, String enumAlias, ClassInfo ci,
			StructuredNumber strucNum) {

		GenericPropertyInfo enumPi = new GenericPropertyInfo(model,
				ci.id() + "_" + enumName, enumName, Options.UNKNOWN);

		// set remaining properties required by Info interface
		if (enumAlias != null && enumAlias.trim().length() > 0) {
			enumPi.setAliasName(enumAlias);
		} else {
			enumPi.setAliasName(enumName);
		}
		enumPi.setDefinition("");
		// no need to set the stereotype in this case
		// enumPi.setStereotypes(null);
		TaggedValues taggedValues = options.taggedValueFactory();
		taggedValues.add("name", "");
		taggedValues.add("profiles", "");
		enumPi.setTaggedValues(taggedValues, false);

		// set remaining properties required by PropertyInfo interface

		enumPi.setDerived(false);
		enumPi.setReadOnly(false);
		enumPi.setAttribute(true);
		Type enumPiType = new Type();
		ClassInfo characterStringCi = model.classByName("CharacterString");
		String characterStringCiId;
		if (characterStringCi == null) {
			characterStringCiId = CHARACTER_STRING_CLASS_ID;
		} else {
			characterStringCiId = characterStringCi.id();
		}
		enumPiType.id = characterStringCiId;
		enumPiType.name = "CharacterString";
		enumPi.setTypeInfo(enumPiType);
		enumPi.setNavigable(true);
		enumPi.setOrdered(false);
		enumPi.setUnique(true);
		enumPi.setComposition(false);
		enumPi.setAggregation(false);
		Multiplicity mult = new Multiplicity();
		mult.maxOccurs = 1;
		mult.minOccurs = 1;
		enumPi.setCardinality(mult);
		enumPi.setInitialValue(null);
		enumPi.setInlineOrByReference("inlineOrByReference");
		enumPi.setDefaultCodeSpace("");
		enumPi.setMetadata(false);
		enumPi.setReverseProperty(null);
		enumPi.setInClass(ci);
		/*
		 * ensure that "sequenceNumber" tagged value is also set
		 */
		enumPi.setSequenceNumber(strucNum, true);
		enumPi.setImplementedByNilReason(false);
		enumPi.setVoidable(false);
		enumPi.setConstraints(null);
		enumPi.setAssociation(null);
		enumPi.setRestriction(false);
		enumPi.setNilReasonAllowed(false);

		return enumPi;
	}

	/**
	 * Creates a string that contains the parts, separated by the given
	 * delimiter (if <code>null</code> it defaults to the empty string). If the
	 * set contains a null element, it is ignored. Joins the parts in the order
	 * returned by the iterator. If order of the set is important, ensure that
	 * an ordered set is used (e.g. TreeSet).
	 *
	 * @param parts
	 * @param delimiter
	 * @return
	 */
	protected String join(Set<String> parts, String delimiter) {

		if (parts == null || parts.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();

		String delim = delimiter == null ? "" : delimiter;

		for (String part : parts) {
			if (part != null) {
				sb.append(part);
			}
			sb.append(delim);
		}

		return sb.substring(0, sb.length() - delim.length());
	}
}

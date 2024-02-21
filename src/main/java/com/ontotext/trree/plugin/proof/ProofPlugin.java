package com.ontotext.trree.plugin.proof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ontotext.trree.AbstractInferencer;
import com.ontotext.trree.AbstractRepositoryConnection;
import com.ontotext.trree.ReportSupportedSolution;
import com.ontotext.trree.StatementIdIterator;
import com.ontotext.trree.SystemGraphs;
import com.ontotext.trree.query.QueryResultIterator;
import com.ontotext.trree.query.StatementSource;
import com.ontotext.trree.sdk.InitReason;
import com.ontotext.trree.sdk.ListPatternInterpreter;
import com.ontotext.trree.sdk.PatternInterpreter;
import com.ontotext.trree.sdk.PluginBase;
import com.ontotext.trree.sdk.PluginConnection;
import com.ontotext.trree.sdk.Preprocessor;
import com.ontotext.trree.sdk.Request;
import com.ontotext.trree.sdk.RequestContext;
import com.ontotext.trree.sdk.RequestOptions;
import com.ontotext.trree.sdk.StatelessPlugin;
import com.ontotext.trree.sdk.StatementIterator;
import com.ontotext.trree.sdk.SystemPlugin;
import com.ontotext.trree.sdk.SystemPluginOptions;
import com.ontotext.trree.sdk.Entities.Scope;
import com.ontotext.trree.sdk.SystemPluginOptions.Option;

/**
 * This is a plugin that can return rules and particular premises that
 * cause a particular statement to be inferred using current inferencer
 * 
 * The approach is to access the inferencer isSupported() method by providing a 
 * suitable handler that handles the reported matches by rule.
 *
 *   if we like to explain an inferred statement: 
 *
 *   PREFIX pr: http://www.ontotext.com/proof/
 *	 PREFIX onto: http://www.ontotext.com/
 *   select * {
 *   	graph onto:implicit {?s ?p ?o}
 *      ?solution pr:explain (?s ?p ?o) .
 *      ?solution pr:rule ?rulename .
 *      ?solution pr:subject ?subj .
 *      ?solution pr:predicate ?pred.
 *      ?solution pr:object ?obj .
 *      ?solution pr:context ?context .
 *   }
 * 
 * @author damyan.ognyanov
 *
 */
public class ProofPlugin extends PluginBase implements StatelessPlugin, SystemPlugin, Preprocessor, PatternInterpreter, ListPatternInterpreter {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	// private key to store the connection in the request context
	private static final String REPOSITORY_CONNECTION = "repconn";
	// private key to store the inferencer in the request context
	private static final String INFERENCER = "infer";

	public static final String NAMESPACE = "http://www.ontotext.com/proof/";

	public static final IRI EXPLAIN_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"explain");
	public static final IRI RULE_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"rule");
	public static final IRI SUBJ_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"subject");
	public static final IRI PRED_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"predicate");
	public static final IRI OBJ_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"object");
	public static final IRI CONTEXT_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"context");
	private final static String KEY_STORAGE = "storage";

	int contextMask = StatementIdIterator.DELETED_STATEMENT_STATUS | StatementIdIterator.SKIP_ON_BROWSE_STATEMENT_STATUS |
			StatementIdIterator.INFERRED_STATEMENT_STATUS;

	long explainId = 0;
	long ruleId = 0;
	long subjId = 0;
	long predId = 0;
	long objId = 0;
	long contextId = 0;

	/**
	 * this is the context implementation where the plugin stores currently running patterns
	 * it just keeps some values using sting keys for further access
	 *
	 */
	class ContextImpl implements RequestContext {
		HashMap<String, Object> map = new HashMap<String, Object>();
		Request request;
		@Override
		public Request getRequest() {
			return request;
		}

		@Override
		public void setRequest(Request request) {
			this.request = request;
		}

		public Object getAttribute(String key) {
			return map.get(key); 
		}
		public void setAttribute(String key, Object value) {
			map.put(key, value); 
		}
		public void removeAttribute(String key) {
			map.remove(key);
		}
	}

	/*
	 * main entry for predicate resolution of the ProvenancePlugin
	 * 
	 * (non-Javadoc)
	 * @see com.ontotext.trree.sdk.PatternInterpreter#interpret(long, long, long, long, com.ontotext.trree.sdk.Statements, com.ontotext.trree.sdk.Entities, com.ontotext.trree.sdk.RequestContext)
	 */
	@Override
	public StatementIterator interpret(long subject, long predicate, long object, long context,
									   PluginConnection pluginConnection, RequestContext requestContext) {
		
		if (predicate != explainId && predicate != ruleId && predicate != contextId &&
				predicate != subjId && predicate != predId && predicate != objId)
			return null;

		// make sure we have the proper request context set when preprocess() has been invoked
		// if not return EMPTY
		ContextImpl ctx = (requestContext instanceof ContextImpl)?(ContextImpl)requestContext:null;

		// not our context
		if (ctx == null)
			return StatementIterator.EMPTY;
		
		if (predicate == ruleId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, 
					pluginConnection.getEntities().put(SimpleValueFactory.getInstance().createLiteral(task.current.rule), Scope.REQUEST), 0);
		} else if (predicate == subjId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[0] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[0], 0);
		} else if (predicate == predId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[1] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[1], 0);
		} else if (predicate == objId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[2] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[2], 0);
		} else if (predicate == contextId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[3] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[3], 0);
		}
		
		// if the predicate is not one of the registered in the ProvenancePlugin return null 
		return null;
	}
	/**
	 * returns some cardinality values for the plugin patterns to make sure
	 * that derivedFrom is evaluated first and binds the solution designator
	 * the solution indicator is used by the assess predicates to get the subject, pred or object of the current solution
	 */
	@Override
	public double estimate(long subject, long predicate, long object, long context, PluginConnection pluginConnection,
						   RequestContext requestContext) {
		// if subject is not bound, any patttern return max value until there is some binding ad subject place
		if (subject == 0)
			return Double.MAX_VALUE;
		// explain fetching predicates
		if (predicate == ruleId || predicate == subjId|| predicate == predId || 
				predicate == objId || predicate == contextId) {
			return 1.0;
		}
		// unknown predicate??? maybe it is good to throw an exception
		return Double.MAX_VALUE;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "proof";
	}

	/**
	 * the plugin uses preprocess to register its request context and access the system options
	 * where the current inferencer and repository connections are placed  
	 */
	@Override
	public RequestContext preprocess(Request request) {
		// create a context instance
		ContextImpl impl = new ContextImpl(); 
		impl.setRequest(request);
		// check if there is a valid request and it has options
		if (request != null ) {
			RequestOptions ops = request.getOptions();
			if (ops != null && ops instanceof SystemPluginOptions) {
				// retrieve the inferencer from the systemPluginOptions instance 
				Object obj = ((SystemPluginOptions)ops).getOption(Option.ACCESS_INFERENCER);
				if (obj instanceof AbstractInferencer) {
					impl.setAttribute(INFERENCER, obj);
				}
				// retrieve the repository connection from the systemPluginOptions instance 
				obj = ((SystemPluginOptions)ops).getOption(Option.ACCESS_REPOSITORY_CONNECTION);
				if (obj instanceof AbstractRepositoryConnection) {
					impl.setAttribute(REPOSITORY_CONNECTION, obj);
				}
			}
		}
		return impl;
	}
	
	/**
	 * init the plugin
	 */
	@Override
	public void initialize(InitReason initReason, PluginConnection pluginConnection) {
		// register the predicates

		explainId = pluginConnection.getEntities().put(EXPLAIN_URI, Scope.SYSTEM);
		ruleId = pluginConnection.getEntities().put(RULE_URI, Scope.SYSTEM);
		subjId = pluginConnection.getEntities().put(SUBJ_URI, Scope.SYSTEM);
		predId = pluginConnection.getEntities().put(PRED_URI, Scope.SYSTEM);
		objId = pluginConnection.getEntities().put(OBJ_URI, Scope.SYSTEM);
		contextId = pluginConnection.getEntities().put(CONTEXT_URI, Scope.SYSTEM);
	}

	@Override
	public double estimate(long subject, long predicate, long[] objects, long context, 
			PluginConnection pluginConnection, RequestContext requestContext) {
		if (predicate == explainId) {
			if (objects.length != 3)
				return Double.MAX_VALUE;
			if (objects[0] == 0 || objects[1] == 0 || objects[2] == 0)
				return Double.MAX_VALUE;
			return 10L;
		}
		return Double.MAX_VALUE;
	}

	@Override
	public StatementIterator interpret(long subject, long predicate, long[] objects, long context,
            PluginConnection pluginConnection, RequestContext requestContext) {
		// make sure we have the proper request context set when preprocess() has been invoked
		// if not return EMPTY
		ContextImpl ctx = (requestContext instanceof ContextImpl)?(ContextImpl)requestContext:null;

		// not our context
		if (ctx == null)
			return StatementIterator.EMPTY;
		
		if (predicate == explainId) {
			if (objects == null || objects.length != 3)
				return StatementIterator.EMPTY;

			long subj = objects[0];
			long pred = objects[1];
			long obj = objects[2];
			// empty if no binding, or some of the nodes is not a regular entity
			if (subj <= 0 || obj <= 0 || pred <= 0)
				return StatementIterator.EMPTY;
			// a context if an explicit exists
			long aContext = 0;
			AbstractInferencer infer = (AbstractInferencer)ctx.getAttribute(INFERENCER);
			if (infer.getInferStatementsFlag() == false)
				return StatementIterator.EMPTY;

			// handle an explicit statement
			AbstractRepositoryConnection conn = (AbstractRepositoryConnection)ctx.getAttribute(REPOSITORY_CONNECTION);
			boolean isExplicit = false;
			boolean isDerivedFromSameAs = false;
			{
				StatementIdIterator iter = conn.getStatements(subj, pred, obj, StatementIdIterator.DELETED_STATEMENT_STATUS | StatementIdIterator.SKIP_ON_BROWSE_STATEMENT_STATUS | StatementIdIterator.INFERRED_STATEMENT_STATUS);
				try {
					isExplicit = iter.hasNext();
					aContext = iter.context;
					// handle if explicit comes from sameAs
					isDerivedFromSameAs = 0 != (iter.status & StatementIdIterator.SKIP_ON_REINFER_STATEMENT_STATUS);
				} finally {
					iter.close();
				}
			}
			// create task associated with the predicate
			// allocate a request scope id
			long reificationId = pluginConnection.getEntities().put(SimpleValueFactory.getInstance().createBNode(), Scope.REQUEST);
			
			// create a Task instance and pass the iterator of the statements from the target graph
			ExplainIter ret = new ExplainIter(ctx, reificationId, subj, pred, obj, 
					isExplicit, isDerivedFromSameAs, aContext);
			// access the inferencers and the repository connection from systemoptions
			ret.infer = infer;
			ret.conn = conn;
			ret.init();
			// store the task into request context  
			ctx.setAttribute(KEY_STORAGE+reificationId, ret);
			
			// return the newly created task instance (it is a valid StatementIterator that could be reevaluated until all solutions are 
			// generated)
			return ret;
		}
		return null;
	}
	
	class ExplainIter extends StatementIterator implements ReportSupportedSolution {
		class Solution {
			String rule;
			ArrayList<long[]> premises;
			Solution(String rule, ArrayList<long[]> premises) {
				this.rule = rule;
				this.premises = premises;
			}
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("rule:").append(rule).append("\n");
				for (long[] p : premises) {
					builder.append(p[0]).append(",").append(p[1]).append(",");
					builder.append(p[2]).append(",").append(p[3]).append("\n");
				}
				return builder.toString();
			}
			@Override
			public boolean equals(Object oObj) {
				if (!(oObj instanceof Solution))
					return false;
				Solution other = (Solution)oObj;
				if (other == this)
					return true;
				if (!other.rule.equals(this.rule))
					return false;
				
				if (other.premises.size() != this.premises.size())
					return false;

				//crosscheck
				for (long[] p : this.premises) {
					boolean exists = false;
					for (long[] o : other.premises) {
						if (o[0] == p[0] && o[1] == p[1] && o[2] == p[2] && o[3]== p[3] && o[4] == p[4]) {
							exists = true;
							break;
						}
					}
					if (!exists)
						return false;
				}
				return true;
			}
		}
		// the request context that stores the instance and the options for that iterator (current inferencer, repository connection etc)
		ContextImpl ctx;
		// the key assigned to that instance to it can be retrieved from the context
		String key;
		// this the the Value(Request scoped bnode) designating the currently running instance (used to fetch the task from the context if multiple instances are 
		// evaluated within same query0
		long reificationId;
		// instance of the inference to work with
		AbstractInferencer infer;
		// connection to the raw data to get only the AXIOM statements
		AbstractRepositoryConnection conn;
		long subj, pred, obj;
		boolean isExplicit = false;
		boolean isDerivedFromSameAs = false;
		long aContext = 0;
		ArrayList<Solution> solutions = new ArrayList<Solution>();
		Iterator<Solution> iter;
		Solution current = null;
		int currentNo = -1;
		long[] values = null;
		public ExplainIter(ContextImpl ctx2, long reificationId2, long subj, long pred, long obj, boolean isExplicit,
				boolean isDerivedFromSameAs, long aContext) {
			ctx = ctx2;
			reificationId = reificationId2;
			this.subj = subj;
			this.pred = pred;
			this.obj = obj;
			this.isExplicit = isExplicit;
			this.isDerivedFromSameAs = isDerivedFromSameAs;
			this.aContext = aContext;
			this.subject = reificationId;
			this.predicate = explainId;
		}
		public void init() {
			if (!isExplicit) {
				infer.isSupported(subj, pred, obj, 0, 0, this);
				iter = solutions.iterator();
				if (iter.hasNext())
					current = iter.next();
				if (current != null) {
					currentNo = 0;
				}
			} else {
				ArrayList<long[]> arr = new ArrayList<long[]>();
				arr.add(new long[] {subj, pred, obj, aContext});
				current = new Solution("explicit", arr);
				currentNo = 0;
				iter = new Iterator<Solution>() {

					@Override
					public boolean hasNext() {
						return false;
					}

					@Override
					public Solution next() {
						return null;
					}
					
				};
			}
		}
		@Override
		public boolean report(String ruleName, QueryResultIterator q) {
			logger.debug("report rule {} for {},{},{}", ruleName, this.subj, this.pred, this.obj);
			while (q.hasNext()) {
				if (q instanceof StatementSource) {
					StatementSource source = (StatementSource)q;
					Iterator<StatementIdIterator> sol = source.solution();
					boolean isSame = false;
					ArrayList<long[]> aSolution = new ArrayList<long[]>();
					while (sol.hasNext()) {
						StatementIdIterator iter = sol.next();
						// try finding an existing explicit or in-context with same subj, pred and obj
						try(StatementIdIterator ctxIter = conn.getStatements(iter.subj, iter.pred, iter.obj, 0, contextMask)) {
							while (ctxIter.hasNext()) {
								if (ctxIter.context != SystemGraphs.EXPLICIT_GRAPH.getId()) {
									iter.context = ctxIter.context;
									iter.status = ctxIter.status;
									break;
								}
								ctxIter.next();
							}
							ctxIter.close();
						}
						if (iter.subj == this.subj && iter.pred == this.pred && iter.obj == this.obj)
							isSame = true;
						aSolution.add(new long[] {iter.subj, iter.pred, iter.obj, iter.context, iter.status});
					}
					Solution solution = new Solution(ruleName, aSolution);
					logger.debug("isSelfReferentioal {} for solution {}", isSame, solution);
					if (!isSame) {
						if (!solutions.contains(solution)) {
							logger.debug("added");
							solutions.add(solution);
						} else {
							logger.debug("already added");
						}
					} else {
						logger.debug("not added - self referential");
					}
				}
				q.next();
			}
			return false;
		}

		@Override
		public void close() {
			current = null;
			solutions = null;
		}

		@Override
		public boolean next() {
			while (current != null) {
				if (currentNo < current.premises.size()) {
					values = current.premises.get(currentNo);
					currentNo ++;
					return true;
				} else {
					values = null;
					currentNo = 0;
					if (iter.hasNext())
						current = iter.next();
					else
						current = null;
				}
			}
			return false;
		}
		@Override
		public AbstractRepositoryConnection getConnection() {
			return conn;
		}
		
	}
}

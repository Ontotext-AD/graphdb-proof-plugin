package com.ontotext.trree.plugin.proof;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.junit.Rule;
import org.junit.Test;

import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.trree.OwlimSchemaRepository;
import com.ontotext.trree.plugin.proof.ProofPlugin;

public class TestExplainWithProofPlugin {
	@Rule
	public TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();
	
	String query = "PREFIX pr: <http://www.ontotext.com/proof/>\r\n" + 
			"PREFIX food: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/food#>\r\n" + 
			"PREFIX onto: <http://www.ontotext.com/>\r\n" + 
			"\r\n" + 
			"select ?ctx ?s ?p ?o ?rule ?context ?subj ?pred ?obj\r\n" + 
			"from named onto:implicit \r\n" + 
			"from named onto:explicit \r\n" + 
			"{\r\n" +
			"#		values (?s ?p ?o) {(food:Fruit UNDEF UNDEF)} \r\n"+
			"		graph ?g {?s ?p ?o} \r\n"+
			"	filter(strstarts(str(?s),str(food:)))\r\n" +
			"     ?ctx pr:explain (?s ?p ?o) .\r\n" + 
			"     ?ctx pr:rule ?rule .\r\n" + 
			"     ?ctx pr:subject ?subj .\r\n" + 
			"     ?ctx pr:predicate ?pred .\r\n" + 
			"     ?ctx pr:object ?obj .\r\n" + 
			"     ?ctx pr:context ?context .\r\n" + 
			"}\r\n" + 
			""; 
	@Test
	public void testBasicInference() throws RepositoryException, MalformedQueryException, QueryEvaluationException, RDFParseException, IOException {
		Map<String, String> params = new HashMap<String, String>();
		OwlimSchemaRepository sail = new OwlimSchemaRepository();
		params.put("register-plugins", ProofPlugin.class.getName());
		sail.setParameters(params);
		SailRepository rep = new SailRepository(sail);
		rep.setDataDir(tmpFolder.newFolder("proof-plugin-explain"));
		rep.initialize();
		try {
			RepositoryConnection conn = rep.getConnection();
			try {
				String dataFile = Thread.currentThread().getContextClassLoader().getResource("provenance-gdb-826/provenance_sample.trig").getFile();
				conn.add(new File(dataFile), "http://base.uri", RDFFormat.TRIG);
				conn.add(OWL.CLASS, RDFS.SUBCLASSOF, RDFS.CLASS);
				TupleQueryResult res = conn.prepareTupleQuery(query).evaluate();
				HashSet<Value> ctxs = new HashSet<Value>();
				int count = 0;
				while (res.hasNext()) {
					BindingSet bs = res.next();
					System.out.println(bs);
					Binding cB = bs.getBinding("ctx");
					assertNotNull("Expected object to be always bound", cB);
					assertNotNull("Expected object to be not null", cB.getValue());
					ctxs.add(cB.getValue());
					count ++;
				}
				assertEquals("total iterations",11,  ctxs.size());
				assertEquals("total results", 13, count);
				res.close();
			} finally {
				conn.close();
			}
		} finally {
			rep.shutDown();
		}
	}

}


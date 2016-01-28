package org.insight_centre.aceis.subscriptions.streams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.deri.cqels.engine.ExecContext;
import org.deri.cqels.engine.RDFStream;
import org.insight_centre.aceis.observations.SensorObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.rdf.model.Statement;

public abstract class SemanticSensorStream extends RDFStream implements Runnable {
	protected Logger logger = LoggerFactory.getLogger(getClass());

	public SemanticSensorStream(ExecContext context, String uri) {
		super(context, uri);
	}

	// private int sleep = 1000;
	protected int sleep = 1000;
	protected boolean stop = false;
	protected SensorObservation currentObservation;
	protected List<String> requestedProperties = new ArrayList<String>();

	public List<String> getRequestedProperties() {
		return requestedProperties;
	}

	public void setRequestedProperties(List<String> requestedProperties) {
		this.requestedProperties = requestedProperties;
	}

	public void setRate(Double rate) {
		sleep = (int) (sleep / rate);
		logger.info("Streamming interval set to: " + sleep + " ms");
	}

	public void stop() {
		if (!stop) {
			stop = true;
			logger.info("Stopping stream: " + this.getURI());
		}
		// ACEISEngine.getSubscriptionManager().getStreamMap().remove(this.getURI());
		// SubscriptionManager.
	}

	protected abstract List<Statement> getStatements(SensorObservation so) throws NumberFormatException, IOException;

	protected abstract SensorObservation createObservation(Object data);

	public SensorObservation getCurrentObservation() {
		return this.currentObservation;
	}
}

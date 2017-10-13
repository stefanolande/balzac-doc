/*
 * Copyright 2017 Nicola Atzei
 */

package it.unica.tcs.lib;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.script.Script;

public class TransactionBuilder implements ITransactionBuilder {

	private static final long LOCKTIME_NOT_SET = -1;
	private static final int OUTINDEX_NOT_SET = -1;
	private final NetworkParameters params;
	
	public TransactionBuilder(NetworkParameters params) {
		this.params = params;
	}
	
	private static final ITransactionBuilder nullTransaction = new ITransactionBuilder() {
		@Override public Transaction toTransaction() { return null; }
		@Override public boolean isReady() { return true; }		
		@Override public int getOutputsSize() { return 0; }
		@Override public int getInputsSize() { return 0; }
		@Override public boolean isCoinbase() { return false; }
		@Override public ITransactionBuilder getInputTransaction(int index) { throw new UnsupportedOperationException(); }
	};
	
	/*
	 * Input internal representation (not visible outside)
	 */
	protected static class Input {
		private final ITransactionBuilder tx;
		private final int outIndex;
		private final ScriptBuilder2 script;
		private final long locktime;
		
		private Input(ITransactionBuilder tx, int outIndex, ScriptBuilder2 script, long locktime) {
			this.tx = tx;
			this.script = script;
			this.outIndex = outIndex;
			this.locktime = locktime;
		}
		
		private static Input of(ITransactionBuilder tx, int index, ScriptBuilder2 script, long locktime){
			return new Input(tx, index, script, locktime);
		}
	}
	
	/*
	 * Output internal representation (not visible outside)
	 */
	protected static class Output {
		private final ScriptBuilder2 script;
		private final Integer value;
		
		private Output(ScriptBuilder2 script, Integer value) {
			this.script = script;
			this.value = value;
		}
		
		private static Output of(ScriptBuilder2 script, Integer value) {
			return new Output(script,value);
		}
	}
	
	/**
	 * <p>Free variables of the transaction. Input/Output script are {@link ScriptBuilder2}, and they can
	 * have free variables that must be a subset of this map.</p>
	 * 
	 * <p>The methods {@link TransactionBuilder#addInput(TransactionBuilder, Map, ScriptBuilder2)} and
	 * {@link TransactionBuilder#addOutput(ScriptBuilder2, int) will check this requirement.
	 */
	private final Map<String,Class<?>> freeVariables = new HashMap<>();
	private final Map<String,Object> freeVarBindings = new HashMap<>();
	private final List<Input> inputs = new ArrayList<>();
	private final List<Output> outputs = new ArrayList<>();
	private long locktime = LOCKTIME_NOT_SET;
	
	@Override
	public int getInputsSize() {
		return inputs.size();
	}

	@Override
	public int getOutputsSize() {
		return outputs.size();
	}

	@Override
	public ITransactionBuilder getInputTransaction(int index) {
		checkArgument(index<=inputs.size(), "'index' is out-of-range: "+index);
		return inputs.get(index).tx;
	}
	
	/**
	 * Add a free variable.
	 * @param name the name of the variable
	 * @param clazz the expected type of the actual value for the variable
	 * @return this builder
	 */
	public TransactionBuilder freeVariable(String name, Class<?> clazz) {
		this.freeVariables.put(name, clazz);
		return this;
	}
	
	/**
	 * Return a copy the free variables.
	 * @return a map containing the free variables
	 */
	public Map<String,Class<?>> getFreeVariables() {
		return new HashMap<>(freeVariables);
	}
	
	/**
	 * Add a free variable binding. 
	 * @param name the name of the free variable.
	 * @param value the value to be bound.
	 * @return this builder
	 * @throws IllegalArgumentException
	 *             if the provided name is not a free variable for this
	 *             transaction, or if the provided value is an not instance of
	 *             the expected class of the free variable.
	 */
	public TransactionBuilder setFreeVariable(String name, Object value) {
		checkState(this.freeVariables.containsKey(name), "'"+name+"' is not a free variable");
		checkState(this.freeVariables.get(name).isInstance(value), "'"+name+"' is associated with class '"+this.freeVariables.get(name)+"', but 'value' is object of class '"+value.getClass()+"'");
		freeVarBindings.put(name, value);
		return this;
	}
	
	
	/**
	 * Add a new transaction input.
	 * <p>This method is only used by {@link CoinbaseTransactionBuilder} to provide a valid input.
	 * In this way, we avoid to expose other implementation details, even to subclasses</p>
	 * @param inputScript the input script that redeem {@code tx} at {@code outIndex}.
	 * @return this builder.
	 * @throws IllegalArgumentException
	 *             if the parent transaction binding does not match its free
	 *             variables, or the input script free variables are not
	 *             contained within this tx free variables.
	 * @see CoinbaseTransactionBuilder
	 */
	protected TransactionBuilder addInput(ScriptBuilder2 inputScript) {
		checkState(this.inputs.size()==0, "addInput(ScriptBuilder2) can be invoked only once");
		return addInput(nullTransaction, OUTINDEX_NOT_SET, inputScript, LOCKTIME_NOT_SET);
	}
	
	/**
	 * Add a new transaction input.
	 * @param tx the parent transaction to redeem.
	 * @param outIndex the index of the output script to redeem.
	 * @param freeVarBindingsOfTx the parent transaction bindings.
	 * @param inputScript the input script that redeem {@code tx} at {@code outIndex}.
	 * @return this builder.
	 * @throws IllegalArgumentException
	 *             if the parent transaction binding does not match its free
	 *             variables, or the input script free variables are not
	 *             contained within this tx free variables.
	 */
	public TransactionBuilder addInput(ITransactionBuilder tx, int outIndex, ScriptBuilder2 inputScript) {
		return addInput(tx, outIndex, inputScript, LOCKTIME_NOT_SET);
	}
	
	/**
	 * Add a new transaction input.
	 * @param tx the parent transaction to redeem.
	 * @param outIndex the index of the output script to redeem.
	 * @param freeVarBindingsOfTx the parent transaction bindings.
	 * @param inputScript the input script that redeem {@code tx} at {@code outIndex}.
	 * @param locktime relative locktime.
	 * @return this builder.
	 * @throws IllegalArgumentException
	 *             if the parent transaction binding does not match its free
	 *             variables, or the input script free variables are not
	 *             contained within this tx free variables.
	 */
	public TransactionBuilder addInput(ITransactionBuilder tx, int outIndex, ScriptBuilder2 inputScript, long locktime) {
		checkArgument(freeVariables.entrySet().containsAll(inputScript.getFreeVariables().entrySet()), "the input script contains free-variables "+inputScript.getFreeVariables().entrySet()+", but the transactions only contains "+freeVariables.entrySet());
		inputs.add(Input.of(tx, outIndex, inputScript, locktime));
		return this;
	}
	
	/**
	 * Add a new transaction output.
	 * @param outputScript the output script.
	 * @param satoshis the amount of satoshis of the output.
	 * @return this builder.
	 * @throws IllegalArgumentException
	 *             if the output script free variables are not contained within
	 *             this tx free variables.
	 */
	public TransactionBuilder addOutput(ScriptBuilder2 outputScript, int satoshis) {
		checkArgument(freeVariables.entrySet().containsAll(outputScript.getFreeVariables().entrySet()), "the output script contains free-variables "+outputScript.getFreeVariables().entrySet()+", but the transactions only contains "+freeVariables.entrySet());
		outputs.add(Output.of(outputScript, satoshis));
		return this;
	}
	
	/**
	 * Set the transaction locktime (absolute locktime which could represent a block number or a timestamp).
	 * @param locktime the value to set.
	 * @return this builder.
	 */
	public TransactionBuilder setLocktime(long locktime) {
		this.locktime = locktime;
		return this;
	}

	/**
	 * Recursively check that this transaction and all the ancestors don't have free variables.
	 * @return true if this transaction and all the ancestors don't have free variables, false otherwise.
	 */
	public boolean isReady() {
		boolean allBound = this.freeVariables.entrySet().stream().allMatch(e->{
			String name = e.getKey();
			Class<?> type = e.getValue();
			return this.freeVarBindings.containsKey(name) && type.isInstance(freeVarBindings.get(name));
		});		
		return allBound && inputs.size()>0 && outputs.size()>0 && 
				inputs.stream().map(x->x.tx).allMatch(ITransactionBuilder::isReady);
	}
	
	/**
	 * Create a bitcoinj transaction. This method assumes that this builder {@link #isReady()} (i.e. has not
	 * unbound free variables.
	 * @param params network parameters.
	 * @return a bitcoinj transaction.
	 */
	@Override
	public Transaction toTransaction() {
		checkState(this.isReady(), "the transaction and all its ancestors are not ready");
		
		Transaction tx = new Transaction(params);
		
		// inputs
		for (Input input : inputs) {
			ITransactionBuilder parentTransaction2 = input.tx;
			
			if (input.outIndex == OUTINDEX_NOT_SET) {
				// coinbase transaction
				byte[] script = new byte[]{};	// script will be set later
				TransactionInput txInput = new TransactionInput(params, tx, script);
				tx.addInput(txInput);
				checkState(txInput.isCoinBase(), "'txInput' is expected to be a coinbase");
			}
			else {
				Transaction parentTransaction = parentTransaction2.toTransaction();
				TransactionOutPoint outPoint = new TransactionOutPoint(params, input.outIndex, parentTransaction);
				byte[] script = new byte[]{};	// script will be set later
				TransactionInput txInput = new TransactionInput(params, tx, script, outPoint);
				
				//set checksequenseverify (relative locktime)
				if (input.locktime==LOCKTIME_NOT_SET) {
					// see BIP-0065
					if (this.locktime!=LOCKTIME_NOT_SET)
						txInput.setSequenceNumber(TransactionInput.NO_SEQUENCE-1);
				}
				else {
					txInput.setSequenceNumber(input.locktime);
				}
//				txInput.setScriptSig(input.script.build());
				tx.addInput(txInput);
			}
		}
				
		// outputs
		for (Output output : outputs) {
			// bind free variables
			ScriptBuilder2 sb = output.script;
			for(Entry<String, Object> freeVar : freeVarBindings.entrySet()) {
				sb = sb.setFreeVariable(freeVar.getKey(), freeVar.getValue());
			}
			checkState(sb.freeVariableSize()==0);
			checkState(sb.signatureSize()==0);
			
			Script outScript = sb.build();
			Coin value = Coin.valueOf(output.value);
			tx.addOutput(value, outScript);
		}
		
		//set checklocktime (absolute locktime)
		if (locktime!=LOCKTIME_NOT_SET) {
			tx.setLockTime(locktime);
		}
		
		// set all the signatures within the input scripts (which are never part of the signature)
		for (int i=0; i<tx.getInputs().size(); i++) {
			TransactionInput txInput = tx.getInputs().get(i);
			ScriptBuilder2 sb = inputs.get(i).script;
			
			// bind free variables
			for(Entry<String, Object> freeVar : freeVarBindings.entrySet()) {
				sb = sb.setFreeVariable(freeVar.getKey(), freeVar.getValue());
			}
			checkState(sb.freeVariableSize()==0, "input script cannot have free variables");
			
			byte[] outScript;
			if (txInput.isCoinBase()) {
				outScript = new byte[]{};
			}
			else {
				if (txInput.getOutpoint().getConnectedOutput().getScriptPubKey().isPayToScriptHash())
					outScript = sb.getLastPush();
				else
					outScript = txInput.getOutpoint().getConnectedPubKeyScript();
			}
			sb = sb.setSignatures(tx, i, outScript);
            checkState(sb.signatureSize()==0,  "all the signatures should have been set");
            
            // update scriptSig
            txInput.setScriptSig(sb.build());
		}
		
		return tx;
	}

	@Override
	public boolean isCoinbase() {
		return inputs.size()==1 && inputs.get(0).tx == nullTransaction;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("TransactionBuilder\n\n");
		if (isCoinbase()) 
			sb.append("        coinbase\n");
		if (freeVarBindings.size()>0) {
			sb.append("\n        bindings : \n");
			for (Entry<String,Object> binding : freeVarBindings.entrySet())
				sb.append("            ").append(binding.getKey()).append(" -> ").append(binding.getValue()).append("\n");
		}
		if (freeVariables.size()>0)
			sb.append("        freeVariables : "+this.freeVariables.keySet()+"\n");
		sb.append("        ready : ").append(isReady()).append("\n");
		
		if (inputs.size()>0) {
			sb.append("\n        inputs : \n");
			for(Input in : inputs) {
				sb.append("            [").append(in.outIndex).append("] ").append(in.script.toString()).append("\n");
			}
		}
		if (outputs.size()>0) {
			sb.append("\n        outputs : \n");
			for(Output out : outputs) {
				sb.append("            [").append(out.value).append("] ").append(out.script.toString()).append("\n");
			}
		}
		return sb.toString();
	}
	
	
	private Set<String> getUnboundFreeVariables() {
		Set<String> s = new HashSet<>(this.freeVariables.keySet());
		s.removeAll(this.freeVarBindings.keySet());
		return s;
	}
}

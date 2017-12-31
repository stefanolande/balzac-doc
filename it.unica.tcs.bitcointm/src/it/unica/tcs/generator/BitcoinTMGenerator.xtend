/*
 * Copyright 2017 Nicola Atzei
 */

/*
 * generated by Xtext 2.11.0
 */
package it.unica.tcs.generator

import com.google.inject.Inject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.generator.AbstractGenerator
import org.eclipse.xtext.generator.IFileSystemAccess2
import org.eclipse.xtext.generator.IGeneratorContext

/**
 * Generates code from your model files on save.
 *
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#code-generation
 */
class BitcoinTMGenerator extends AbstractGenerator {

//    @Inject private TransactionFactoryGenerator txFactoryGenerator
//    @Inject private ParticipantGenerator participantGenerator
    @Inject private RawTransactionGenerator rawTxGenerator

    override void doGenerate(Resource resource, IFileSystemAccess2 fsa, IGeneratorContext context) {
//      txFactoryGenerator.doGenerate(resource,fsa,context)
//      participantGenerator.doGenerate(resource,fsa,context)
        rawTxGenerator.doGenerate(resource,fsa,context)
    }

}

package com.equiptrack.backend.equipamento;

/** Situação do equipamento, igual ao ENUM {@code situacao} do DER. */
public enum Situacao {
	DISPONIVEL, EMPRESTADO, DANIFICADO, EM_MANUTENCAO;

	/** EMPRESTADO só surge pela retirada de um empréstimo, nunca no cadastro (RF04). */
	public boolean permitidaNoCadastro() {
		return this != EMPRESTADO;
	}
}

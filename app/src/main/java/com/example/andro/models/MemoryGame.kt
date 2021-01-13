package com.example.andro.models

import com.example.andro.utils.DEFAULT_ICONS

class MemoryGame(private val boardSize: BoardSize, private val customImages: List<String>?){

    val cards: List<MemoryCard>
    var numPairFound = 0

    private var numCardFlips = 0
    private var indexOfSingleSelectedCard: Int? = null
    init {
        if(customImages == null){

        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumsPairs())
        val randomizeImage = (chosenImages + chosenImages).shuffled()
        cards = randomizeImage.map { MemoryCard(it) }
        }else{
            val randomizeImages = (customImages + customImages).shuffled()
            cards  = randomizeImages.map { MemoryCard(it.hashCode(), it) }
        }
    }

    fun flipCard(position: Int): Boolean {
        numCardFlips++
        val card = cards[position]
        var foundMatch = false
        if(indexOfSingleSelectedCard==null){
            restorCard()
            indexOfSingleSelectedCard=position
        } else{
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!, position)
            indexOfSingleSelectedCard = null
        }
        card.isFaseUp = !card.isFaseUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if(cards[position1].identifer != cards[position2].identifer){
            return false
        }
        cards[position1].isMatched =true
        cards[position2].isMatched =true
        numPairFound++
        return true
    }

    private fun restorCard() {
        for (card in cards) {
            if(!card.isMatched){
                card.isFaseUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return numPairFound == boardSize.getNumsPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaseUp
    }

    fun getNumMoves(): Int {
        return numCardFlips /2
    }
}

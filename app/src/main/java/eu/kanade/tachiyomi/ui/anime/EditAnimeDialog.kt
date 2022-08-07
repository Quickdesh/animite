// AM -->
package eu.kanade.tachiyomi.ui.anime

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.anime.interactor.GetAnimeById
import eu.kanade.domain.anime.model.Anime
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.LocalAnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.databinding.EditAnimeDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.dropBlank
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.trimOrNull
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditAnimeDialog : DialogController {

    private lateinit var binding: EditAnimeDialogBinding

    private val anime: Anime

    private val infoController
        get() = targetController as AnimeController

    private val context: Context get() = binding.root.context

    constructor(target: AnimeController, anime: Anime) : super(
        bundleOf(KEY_ANIME to anime.id),
    ) {
        targetController = target
        this.anime = anime
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        anime = runBlocking { Injekt.get<GetAnimeById>().await(bundle.getLong(KEY_ANIME))!! }
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditAnimeDialogBinding.inflate(activity!!.layoutInflater)
        val view = ScrollView(activity!!).apply {
            addView(binding.root)
        }
        onViewCreated()
        return MaterialAlertDialogBuilder(activity!!)
            .setView(view)
            .setPositiveButton(R.string.action_save) { _, _ -> onPositiveButtonClick() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    fun onViewCreated() {
        loadCover()

        val isLocal = anime.source == LocalAnimeSource.ID

        val statusAdapter: ArrayAdapter<String> = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                R.string.label_default,
                R.string.ongoing,
                R.string.completed,
                R.string.licensed,
                R.string.publishing_finished,
                R.string.cancelled,
                R.string.on_hiatus,
            ).map { context.getString(it) },
        )

        binding.status.adapter = statusAdapter
        if (anime.status != anime.ogStatus) {
            binding.status.setSelection(
                when (anime.status.toInt()) {
                    SAnime.UNKNOWN -> 0
                    SAnime.ONGOING -> 1
                    SAnime.COMPLETED -> 2
                    SAnime.LICENSED -> 3
                    SAnime.PUBLISHING_FINISHED, 61 -> 4
                    SAnime.CANCELLED, 62 -> 5
                    SAnime.ON_HIATUS, 63 -> 6
                    else -> 0
                },
            )
        }

        if (isLocal) {
            if (anime.title != anime.url) {
                binding.title.setText(anime.title)
            }

            binding.title.hint = context.getString(R.string.title_hint, anime.url)
            binding.animeAuthor.setText(anime.author.orEmpty())
            binding.animeArtist.setText(anime.artist.orEmpty())
            binding.animeDescription.setText(anime.description.orEmpty())
            binding.animeGenresTags.setChips(anime.genre.orEmpty().dropBlank())
        } else {
            if (anime.title != anime.ogTitle) {
                binding.title.append(anime.title)
            }
            if (anime.author != anime.ogAuthor) {
                binding.animeAuthor.append(anime.author.orEmpty())
            }
            if (anime.artist != anime.ogArtist) {
                binding.animeArtist.append(anime.artist.orEmpty())
            }
            if (anime.description != anime.ogDescription) {
                binding.animeDescription.append(anime.description.orEmpty())
            }
            binding.animeGenresTags.setChips(anime.genre.orEmpty().dropBlank())

            binding.title.hint = context.getString(R.string.title_hint, anime.ogTitle)
            if (anime.ogAuthor != null) {
                binding.animeAuthor.hint = context.getString(R.string.studio_hint, anime.ogAuthor)
            }
            if (anime.ogArtist != null) {
                binding.animeArtist.hint = context.getString(R.string.artist_hint, anime.ogArtist)
            }
            if (!anime.ogDescription.isNullOrBlank()) {
                binding.animeDescription.hint =
                    context.getString(
                        R.string.description_hint,
                        anime.ogDescription.replace("\n", " ").chop(20),
                    )
            }
        }
        binding.animeGenresTags.clearFocus()

        binding.resetTags.clicks()
            .onEach { resetTags() }
            .launchIn(infoController.viewScope)
    }

    private fun resetTags() {
        if (anime.genre.isNullOrEmpty() || anime.source == LocalAnimeSource.ID) {
            binding.animeGenresTags.setChips(emptyList())
        } else {
            binding.animeGenresTags.setChips(anime.ogGenre.orEmpty())
        }
    }

    private fun loadCover() {
        val radius = context.resources.getDimension(R.dimen.card_radius)
        binding.animeCover.load(anime) {
            transformations(RoundedCornersTransformation(radius))
        }
    }

    private fun onPositiveButtonClick() {
        infoController.presenter.updateAnimeInfo(
            binding.title.text.toString(),
            binding.animeAuthor.text.toString(),
            binding.animeArtist.text.toString(),
            binding.animeDescription.text.toString(),
            binding.animeGenresTags.getTextStrings(),
            binding.status.selectedItemPosition.let {
                when (it) {
                    1 -> SAnime.ONGOING
                    2 -> SAnime.COMPLETED
                    3 -> SAnime.LICENSED
                    4 -> SAnime.PUBLISHING_FINISHED
                    5 -> SAnime.CANCELLED
                    6 -> SAnime.ON_HIATUS
                    else -> null
                }
            }?.toLong(),
        )
    }

    private fun ChipGroup.setChips(items: List<String>) {
        removeAllViews()

        items.asSequence().map { item ->
            Chip(context).apply {
                text = item

                isCloseIconVisible = true
                closeIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
                setOnCloseIconClickListener {
                    removeView(this)
                }
            }
        }.forEach {
            addView(it)
        }

        val addTagChip = Chip(context).apply {
            setText(R.string.add_tag)

            chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_24dp)?.apply {
                isChipIconVisible = true
                setTint(context.getResourceColor(R.attr.colorAccent))
            }

            clicks().onEach {
                var newTag: String? = null
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.add_tag)
                    .setTextInput {
                        newTag = it.trimOrNull()
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (newTag != null) setChips(items + listOfNotNull(newTag))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }.launchIn(infoController.viewScope)
        }
        addView(addTagChip)
    }

    private fun ChipGroup.getTextStrings(): List<String> = children.mapNotNull {
        if (it is Chip && !it.text.toString().contains(context.getString(R.string.add_tag), ignoreCase = true)) {
            it.text.toString()
        } else null
    }.toList()

    private companion object {
        const val KEY_ANIME = "anime_id"
    }
}
// AM <--
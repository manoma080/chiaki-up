// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.gameblabla.chiaki.settings

import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.core.view.WindowCompat

import com.gameblabla.chiaki.R
import com.gameblabla.chiaki.databinding.ActivitySettingsBinding

interface TitleFragment
{
	fun getTitle(resources: Resources): String
}

class SettingsActivity: AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
{
	private lateinit var binding: ActivitySettingsBinding

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		
		WindowCompat.setDecorFitsSystemWindows(window, false)
		
		binding = ActivitySettingsBinding.inflate(layoutInflater)
		setContentView(binding.root)
		title = ""
		setSupportActionBar(binding.toolbar)

		if(savedInstanceState == null)
			replaceFragment(SettingsFragment(), false)
		supportFragmentManager.addOnBackStackChangedListener { updateTitle() }
		updateTitle()
	}

	private fun updateTitle()
	{
		val titleFragment = supportFragmentManager.findFragmentById(R.id.settingsFragment) as? TitleFragment
		binding.titleTextView.text = titleFragment?.getTitle(resources) ?: getString(R.string.title_settings)
	}

	override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference) = when(pref.fragment)
	{
		SettingsRegisteredHostsFragment::class.java.canonicalName -> {
			replaceFragment(SettingsRegisteredHostsFragment(), true)
			true
		}
		SettingsVideoFiltersFragment::class.java.canonicalName -> {
			replaceFragment(SettingsVideoFiltersFragment(), true)
			true
		}
		else -> false
	}

	private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean)
	{
		supportFragmentManager.beginTransaction()
			.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
			.replace(R.id.settingsFragment, fragment)
			.also {
				if(addToBackStack)
					it.addToBackStack(null)
			}
			.commit()
	}
}
